package pro.softcom.sentinelle.infrastructure.confluence.adapter.in.mapper;

import java.util.List;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto.ConfluencePageDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto.ConfluenceSpaceDto;

/**
 * Mapper d'adaptateur pour convertir les objets du domaine Confluence vers des DTO d'API.
 */
public final class ConfluenceApiMapper {

    private ConfluenceApiMapper() {
        // util
    }

    public static ConfluencePageDto toDto(ConfluencePage page) {
        if (page == null) {
            return null;
        }
        var content = page.content();
        return ConfluencePageDto.builder()
            .id(page.id())
            .title(page.title())
            .spaceKey(page.spaceKey())
            .content(content != null ? content.body() : null)
            .contentFormat(content != null ? content.format() : null)
            .labels(page.labels())
            .build();
    }

    public static List<ConfluencePageDto> toDtoPages(List<ConfluencePage> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        return pages.stream().map(ConfluenceApiMapper::toDto).toList();
    }

    public static ConfluenceSpaceDto toDto(ConfluenceSpace space) {
        if (space == null) {
            return null;
        }
        return ConfluenceSpaceDto.builder()
            .id(space.id())
            .key(space.key())
            .name(space.name())
            .url(space.url())
            .description(space.description())
            .type(space.type() != null ? space.type().getValue() : null)
            .status(space.status() != null ? space.status().getValue() : null)
            .build();
    }

    public static List<ConfluenceSpaceDto> toDtoSpaces(List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return List.of();
        }
        return spaces.stream().map(ConfluenceApiMapper::toDto).toList();
    }
}
