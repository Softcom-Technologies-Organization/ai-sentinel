package pro.softcom.sentinelle.application.pii.reporting.usecase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.reporting.port.in.DetectionReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceScanSpaceStatus;

/**
 * Application service exposing the latest scan and per-space statuses for presentation layer.
 */
@RequiredArgsConstructor
@Slf4j
public class ScanResultUseCaseImpl implements DetectionReportingUseCase {

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
    public List<ConfluenceScanSpaceStatus> getLatestScanSpaceStatuses(String scanId) {
        if (scanId == null || scanId.isBlank()) return List.of();

        // 1) Load checkpoint statuses (may be empty if no checkpoint yet for a space)
        Map<String, String> statuses = new HashMap<>();
        try {
            List<ScanCheckpoint> cps = checkpointRepo.findByScan(scanId);
            for (ScanCheckpoint cp : cps) {
                statuses.put(cp.spaceKey(), cp.scanStatus().name());
            }
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to load checkpoint statuses: {}", ex.getMessage());
        }

        // 2) Load counters from events per space via read port
        try {
            return scanResultQuery.getSpaceCounters(scanId).stream()
                .map(c -> new ConfluenceScanSpaceStatus(
                    c.spaceKey(),
                    mapPresentationStatus(statuses.get(c.spaceKey()), c.pagesDone(), c.attachmentsDone()),
                    c.pagesDone(),
                    c.attachmentsDone(),
                    c.lastEventTs()
                ))
                .toList();
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to get space statuses for {}: {}", scanId, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ScanResult> getLatestScanItems() {
        try {
            Optional<LastScanMeta> meta = scanResultQuery.findLatestScan();
            if (meta.isEmpty()) return List.of();
            return scanResultQuery.listItemEvents(meta.get().scanId());
        } catch (Exception ex) {
            log.warn("[LAST_SCAN] Failed to get latest scan items: {}", ex.getMessage());
            return List.of();
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
