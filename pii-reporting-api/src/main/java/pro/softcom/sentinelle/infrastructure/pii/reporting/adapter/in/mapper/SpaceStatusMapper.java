package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceScanSpaceStatus;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.SpaceScanStateDto;

/**
 * Maps domain per-space status to web DTOs.
 */
@Component
public class SpaceStatusMapper {

    public SpaceScanStateDto toDto(ConfluenceScanSpaceStatus s) {
        if (s == null) return null;
        return new SpaceScanStateDto(s.spaceKey(), s.status(), s.pagesDone(), s.attachmentsDone(), s.lastEventTs());
    }

    public List<SpaceScanStateDto> toDtoList(List<ConfluenceScanSpaceStatus> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(this::toDto).toList();
    }
}
