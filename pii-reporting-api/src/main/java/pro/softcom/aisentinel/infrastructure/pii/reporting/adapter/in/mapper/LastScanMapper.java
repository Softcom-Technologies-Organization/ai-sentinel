package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.LastScanDto;

/**
 * Maps domain objects related to scan results to web DTOs.
 * Keeps controllers thin and free of mapping logic.
 */
@Component
public class LastScanMapper {

    public LastScanDto toDto(LastScanMeta meta) {
        if (meta == null) {
            return null;
        }
        return new LastScanDto(meta.scanId(), meta.lastUpdated(), meta.spacesCount());
    }
}
