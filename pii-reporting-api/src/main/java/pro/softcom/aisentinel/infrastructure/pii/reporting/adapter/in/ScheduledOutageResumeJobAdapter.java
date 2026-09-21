package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.OutagePausedScans;

/**
 * Driving adapter that retries the scans an outage paused — a timer in the place a browser
 * would otherwise be, which is why it sits next to the controllers rather than in adapter.out.
 * <p>
 * Business purpose: a scan that outlives the VPN session used to stop for good until an
 * operator came back and pressed Resume — so a scan of a large base could never run
 * overnight. Retrying on a timer turns a dropped session into a pause of a few minutes.
 * <p>
 * A retry that still finds the outage pauses the scan again, which puts it straight back
 * on the list, so no extra reachability probe is needed here. A pause the operator asked
 * for is never on the list and is never retried.
 * <p>
 * Can be disabled via property: scan.auto-resume.enabled=false
 */
@Component
@ConditionalOnProperty(name = "scan.auto-resume.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ScheduledOutageResumeJobAdapter {

    private final OutagePausedScans outagePausedScans;
    private final StreamConfluenceResumeScanPort streamConfluenceResumeScanPort;
    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestratorPort;

    @Scheduled(fixedRateString = "${scan.auto-resume.interval-ms:120000}",
               initialDelayString = "${scan.auto-resume.interval-ms:120000}")
    public void retryScansPausedByAnOutage() {
        // Never alongside a scan that is already running, even a different one:
        // severity counts are global and additive, so two pipelines writing at once
        // would inflate the dashboard. The waiting scan simply gets the next tick.
        if (scanExecutionOrchestratorPort.hasActiveScan()) {
            return;
        }
        for (String scanId : outagePausedScans.snapshot()) {
            log.info("[AUTO-RESUME] Retrying scan {} paused by an outage", scanId);
            try {
                // The returned stream is for a browser to watch; the work itself runs on the
                // independent subscription the resume registers, so dropping it here starts
                // the scan rather than discarding it.
                streamConfluenceResumeScanPort.resumeAllSpaces(scanId);
                return; // one at a time, for the same reason as the guard above
            } catch (Exception exception) {
                log.error("[AUTO-RESUME] Retry of scan {} failed to start: {}", scanId,
                          exception.getMessage());
            }
        }
    }
}
