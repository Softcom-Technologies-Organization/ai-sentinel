package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ConfluenceContentScanResultEventDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.LastScanDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanReportingSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SpaceScanStateDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.LastScanMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanReportingSummaryMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanResultToScanEventMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.SpaceStatusMapper;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
public class LastConfluencePersonallyIdentifiableInformationScanController {

    private final ScanReportingPort scanReportingPort;
    private final LastScanMapper lastScanMapper;
    private final SpaceStatusMapper spaceStatusMapper;
    private final ScanResultToScanEventMapper scanResultToScanEventMapper;
    private final ScanReportingSummaryMapper scanReportingSummaryMapper;

    @GetMapping("/last")
    public ResponseEntity<@NonNull LastScanDto> getLastScan() {
        return scanReportingPort.getLatestScan()
                .map(lastScanMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/last/spaces")
    public ResponseEntity<@NonNull List<SpaceScanStateDto>> getLastScanSpaceStatuses() {
        return scanReportingPort.getLatestScan()
                .map(meta -> {
                    List<ConfluenceSpaceScanState> list = scanReportingPort.getLatestSpaceScanStateList(
                        meta.scanId());
                    return ResponseEntity.ok(spaceStatusMapper.toDtoList(list));
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/last/items")
    public ResponseEntity<@NonNull List<ConfluenceContentScanResultEventDto>> getLastScanItems() {
        List<ConfluenceContentScanResultEventDto> items = scanReportingPort.getLatestSpaceScanResultList().stream()
                .map(scanResultToScanEventMapper::toDto)
                .toList();
        if (items.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(items);
    }

    /**
     * Unified endpoint combining authoritative status/progress from scan_checkpoints
     * with aggregated counters from scan_events.
     * Provides single source of truth for dashboard to eliminate race conditions
     * and incorrect progress displays.
     */
    @GetMapping("/dashboard/spaces-summary")
    public ResponseEntity<@NonNull ScanReportingSummaryDto> getDashboardSpacesSummary() {
        return scanReportingPort.getLatestScan()
                .flatMap(meta -> scanReportingPort.getScanReportingSummary(meta.scanId()))
                .map(scanReportingSummaryMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
