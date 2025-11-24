package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SpaceScanStateDto;

@Component
public class SpaceStatusMapper {

    public List<SpaceScanStateDto> toDtoList(List<ConfluenceSpaceScanState> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(this::toDto).toList();
    }

    public SpaceScanStateDto toDto(ConfluenceSpaceScanState s) {
        if (s == null) return null;
        return new SpaceScanStateDto(s.spaceKey(), s.status(), s.pagesDone(), s.attachmentsDone(), s.lastEventTs(), s.progressPercentage());
    }
}
