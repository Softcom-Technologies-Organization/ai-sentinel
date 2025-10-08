package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.ScanReportingUseCase;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceScanSpaceStatus;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.LastScanDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.SpaceScanStateDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper.LastScanMapper;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper.ScanResultToScanEventMapper;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper.SpaceStatusMapper;

/**
 * REST endpoints to retrieve latest scan information for the dashboard.
 * Rule: always pick the most recent scan (even if interrupted).
 */
@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
public class LastScanController {

    private final ScanReportingUseCase scanReportingUseCase;
    private final LastScanMapper lastScanMapper;
    private final SpaceStatusMapper spaceStatusMapper;
    private final ScanResultToScanEventMapper scanResultToScanEventMapper;

    @GetMapping("/last")
    public ResponseEntity<@NonNull LastScanDto> getLastScan() {
        return scanReportingUseCase.getLatestScan()
                .map(lastScanMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/last/spaces")
    public ResponseEntity<@NonNull List<SpaceScanStateDto>> getLastScanSpaceStatuses() {
        return scanReportingUseCase.getLatestScan()
                .map(meta -> {
                    List<ConfluenceScanSpaceStatus> list = scanReportingUseCase.getLatestSpaceScanStateList(meta.scanId());
                    return ResponseEntity.ok(spaceStatusMapper.toDtoList(list));
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/last/items")
    public ResponseEntity<@NonNull List<ScanEventDto>> getLastScanItems() {
        List<ScanEventDto> items = scanReportingUseCase.getLatestScanItems().stream()
                .map(scanResultToScanEventMapper::toDto)
                .toList();
        if (items.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(items);
    }
}
