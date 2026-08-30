package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.reporting.port.in.GetScanSpaceStatsPort;
import pro.softcom.aisentinel.domain.pii.scan.ScanSpaceStatsNotFoundException;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanSpaceStatsDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanSpaceStatsMapper;

/**
 * Exposes the latest-scan statistics of a space for the dashboard tooltip.
 */
@RestController
@RequestMapping("/api/v1/scans/dashboard/spaces")
@RequiredArgsConstructor
public class ScanSpaceStatsController {

    private final GetScanSpaceStatsPort getScanSpaceStatsPort;
    private final ScanSpaceStatsMapper scanSpaceStatsMapper;

    /**
     * Returns the aggregated stats of the latest scan for a space.
     *
     * @param spaceKey Confluence space key
     * @return 200 with the stats, or 404 when no stats exist for the space
     */
    @GetMapping("/{spaceKey}/stats")
    public ResponseEntity<@NonNull ScanSpaceStatsDto> getSpaceStats(@PathVariable String spaceKey) {
        return getScanSpaceStatsPort.getLatestSpaceStats(spaceKey)
            .map(scanSpaceStatsMapper::toDto)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ScanSpaceStatsNotFoundException(spaceKey));
    }
}
