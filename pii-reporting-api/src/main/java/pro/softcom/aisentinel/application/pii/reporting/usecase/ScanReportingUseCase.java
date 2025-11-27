package pro.softcom.aisentinel.application.pii.reporting.usecase;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.reporting.SpaceSummary;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;

@RequiredArgsConstructor
@Slf4j
public class ScanReportingUseCase implements ScanReportingPort {

    private final ScanResultQuery scanResultQuery;
    private final ScanCheckpointRepository checkpointRepo;

    @Override
    public Optional<LastScanMeta> getLatestScan() {
        try {
            return scanResultQuery.findLatestScan();
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to get latest scan: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ConfluenceSpaceScanState> getLatestSpaceScanStateList(String scanId) {
        if (scanId == null || scanId.isBlank()) return List.of();

        // 1) Load checkpoint statuses and progress percentages (may be empty if no checkpoint yet for a space)
        Map<String, String> statuses = new HashMap<>();
        Map<String, Double> progressPercentages = new HashMap<>();
        try {
            List<ScanCheckpoint> cps = checkpointRepo.findByScan(scanId);
            for (ScanCheckpoint cp : cps) {
                statuses.put(cp.spaceKey(), cp.scanStatus().name());
                progressPercentages.put(cp.spaceKey(), cp.progressPercentage());
            }
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to load checkpoint statuses: {}", ex.getMessage());
        }

        // 2) Load counters from events per space via read port
        try {
            return scanResultQuery.getSpaceCounters(scanId).stream()
                .map(c -> new ConfluenceSpaceScanState(
                    c.spaceKey(),
                    mapPresentationStatus(statuses.get(c.spaceKey()), c.pagesDone(), c.attachmentsDone()),
                    c.pagesDone(),
                    c.attachmentsDone(),
                    c.lastEventTs(),
                    progressPercentages.get(c.spaceKey())
                ))
                .toList();
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to get space statuses for {}: {}", scanId, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ConfluenceContentScanResult> getLatestSpaceScanResultList() {
        try {
            Optional<LastScanMeta> meta = scanResultQuery.findLatestScan();
            if (meta.isEmpty()) return List.of();
            return scanResultQuery.listItemEventsEncrypted(meta.get().scanId());
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to get latest scan items: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<ScanReportingSummary> getScanReportingSummary(String scanId) {
        if (scanId == null || scanId.isBlank()) return Optional.empty();

        try {
            // 1) Load checkpoint statuses and progress percentages
            Map<String, String> statuses = new HashMap<>();
            Map<String, Double> progressPercentages = new HashMap<>();
            List<ScanCheckpoint> cps = checkpointRepo.findByScan(scanId);
            for (ScanCheckpoint cp : cps) {
                statuses.put(cp.spaceKey(), cp.scanStatus().name());
                progressPercentages.put(cp.spaceKey(), cp.progressPercentage());
            }

            // 2) Load counters from events per space
            List<SpaceSummary> spaces = scanResultQuery.getSpaceCounters(scanId).stream()
                .map(c -> new SpaceSummary(
                    c.spaceKey(),
                    mapPresentationStatus(statuses.get(c.spaceKey()), c.pagesDone(), c.attachmentsDone()),
                    progressPercentages.get(c.spaceKey()),
                    c.pagesDone(),
                    c.attachmentsDone(),
                    c.lastEventTs()
                ))
                .toList();

            // 3) Find most recent timestamp
            Instant lastUpdated = spaces.stream()
                .map(SpaceSummary::lastEventTs)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(Instant.now());

            // 4) Build nbOfDetectedPIIBySeverity
            return Optional.of(new ScanReportingSummary(
                scanId,
                lastUpdated,
                spaces.size(),
                spaces
            ));
        } catch (Exception ex) {
            log.warn("[DASHBOARD] Failed to get dashboard nbOfDetectedPIIBySeverity for {}: {}", scanId, ex.getMessage());
            return Optional.empty();
        }
    }

    private String mapPresentationStatus(String checkpointStatus, long pagesDone, long attachmentsDone) {
        try {
            if (checkpointStatus != null) {
                switch (checkpointStatus) {
                    case "COMPLETED", "FAILED", "RUNNING":
                        return checkpointStatus;
                    case "CANCELLED":
                        return "PAUSED";
                    default:
                        // fall-through to compute from progress
                }
            }
            long progress = Math.max(0, pagesDone) + Math.max(0, attachmentsDone);
            return progress > 0 ? "PAUSED" : "PENDING";
        } catch (Exception _) {
            return "PENDING";
        }
    }
}
