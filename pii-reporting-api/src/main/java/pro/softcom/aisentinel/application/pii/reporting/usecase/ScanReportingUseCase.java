package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.reporting.*;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;

import java.time.Instant;
import java.util.*;

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
    public List<ConfluenceContentScanResult> getGlobalScanItemsEncrypted() {
        try {
            // 1) Find the latest checkpoint for every space
            List<ScanCheckpoint> latestCheckpoints = checkpointRepo.findAllLatestCheckpoints();
            log.info("[SCAN] Found {} latest checkpoints for global items aggregation", latestCheckpoints.size());
            List<ConfluenceContentScanResult> allItems = new ArrayList<>();

            for (ScanCheckpoint cp : latestCheckpoints) {
                log.info("[SCAN] Processing checkpoint: space={}, scanId={}", cp.spaceKey(), cp.scanId());
                // 2) Load items for this specific (scanId, spaceKey) pair
                List<ConfluenceContentScanResult> spaceItems = scanResultQuery.listItemEventsEncryptedByScanIdAndSpaceKey(
                    cp.scanId(),
                    cp.spaceKey()
                );
                log.info("[SCAN] Found {} items for space={}, scanId={}", spaceItems.size(), cp.spaceKey(), cp.scanId());
                allItems.addAll(spaceItems);
            }
            return allItems;
        } catch (Exception ex) {
            log.warn("[SCAN] Failed to get global scan items: {}", ex.getMessage());
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

            // 4) Build ScanReportingSummary
            return Optional.of(new ScanReportingSummary(
                scanId,
                lastUpdated,
                spaces.size(),
                spaces
            ));
        } catch (Exception ex) {
            log.warn("[SCAN] Failed to get dashboard ScanReportingSummary for {}: {}", scanId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<ScanReportingSummary> getGlobalScanSummary() {
        try {
            // 1) Find the latest checkpoint for every space
            List<ScanCheckpoint> latestCheckpoints = checkpointRepo.findAllLatestCheckpoints();
            if (latestCheckpoints.isEmpty()) {
                return Optional.empty();
            }

            // 2) Collect relevant scanIds
            Set<String> scanIds = new HashSet<>();
            for (ScanCheckpoint cp : latestCheckpoints) {
                scanIds.add(cp.scanId());
            }

            // 3) Load counters for these scanIds
            Map<String, List<ScanResultQuery.SpaceCounter>> countersByScan = new HashMap<>();
            for (String scanId : scanIds) {
                countersByScan.put(scanId, scanResultQuery.getSpaceCounters(scanId));
            }

            // 4) Build space summaries
            List<SpaceSummary> spaces = new ArrayList<>();
            for (ScanCheckpoint cp : latestCheckpoints) {
                List<ScanResultQuery.SpaceCounter> scanCounters = countersByScan.get(cp.scanId());

                long pagesDone = 0;
                long attachmentsDone = 0;
                Instant lastEventTs = null;

                if (scanCounters != null) {
                    for (ScanResultQuery.SpaceCounter sc : scanCounters) {
                        if (sc.spaceKey().equals(cp.spaceKey())) {
                            pagesDone = sc.pagesDone();
                            attachmentsDone = sc.attachmentsDone();
                            lastEventTs = sc.lastEventTs();
                            break;
                        }
                    }
                }

                spaces.add(new SpaceSummary(
                    cp.spaceKey(),
                    mapPresentationStatus(cp.scanStatus().name(), pagesDone, attachmentsDone),
                    cp.progressPercentage(),
                    pagesDone,
                    attachmentsDone,
                    lastEventTs
                ));
            }

            // 5) Determine global meta info
            Optional<LastScanMeta> latestMeta = getLatestScan();
            String globalScanId = latestMeta.map(LastScanMeta::scanId).orElse(
                latestCheckpoints.getFirst().scanId()
            );

            Instant globalLastUpdated = spaces.stream()
                .map(SpaceSummary::lastEventTs)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(Instant.now());

            return Optional.of(new ScanReportingSummary(
                globalScanId,
                globalLastUpdated,
                spaces.size(),
                spaces
            ));

        } catch (Exception ex) {
            log.warn("[SCAN] Failed to get global ScanReportingSummary: {}", ex.getMessage());
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