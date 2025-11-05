package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;

/**
 * Maps ConfluenceSpaceDto (REST) to ConfluenceSpace (domain).
 * URL is presentation-facing; by default, we keep it null here to avoid leaking UI concerns.
 * An overloaded method allows providing a ConfluenceUrlBuilder to populate the URL when required.
 */
@Slf4j
public final class ConfluenceSpaceMapper {

    private ConfluenceSpaceMapper() {
        // utility class
    }


    /**
     * Maps a DTO to domain and builds the UI url using the given builder.
     * If builder is null or cannot build, url remains null.
     * @param dto source dto (must not be null)
     * @return domain model with url populated when possible
     */
    public static ConfluenceSpace toDomain(ConfluenceSpaceDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ConfluenceSpaceDto cannot be null");
        }
        var spaceType = parseSpaceType(dto.type());
        var spaceStatus = parseSpaceStatus(dto.status());
        var descriptionText = extractDescription(dto);
        String url = ConfluenceUrlBuilder.spaceOverviewUrl(dto.key());
        Instant lastModified = extractLastModified(dto);

        return new ConfluenceSpace(
            dto.id(),
            dto.key(),
            dto.name(),
            url,
            descriptionText,
            spaceType,
            spaceStatus,
            lastModified
        );
    }

    private static ConfluenceSpace.SpaceType parseSpaceType(String type) {
        if (type == null) return ConfluenceSpace.SpaceType.GLOBAL;
        return switch (type.toLowerCase()) {
            case "personal" -> ConfluenceSpace.SpaceType.PERSONAL;
            case "project" -> ConfluenceSpace.SpaceType.PROJECT;
            case "team" -> ConfluenceSpace.SpaceType.TEAM;
            default -> ConfluenceSpace.SpaceType.GLOBAL;
        };
    }

    private static ConfluenceSpace.SpaceStatus parseSpaceStatus(String status) {
        if (status == null) {
            return ConfluenceSpace.SpaceStatus.CURRENT;
        }
        return "archived".equalsIgnoreCase(status) ? ConfluenceSpace.SpaceStatus.ARCHIVED : ConfluenceSpace.SpaceStatus.CURRENT;
    }

    private static String extractDescription(ConfluenceSpaceDto dto) {
        var description = dto.description();
        if (description != null && description.plain() != null) {
            return description.plain().value();
        }
        return "";
    }

    //TODO refactor to reduce cyclomatic complexity to 5
    private static Instant extractLastModified(ConfluenceSpaceDto dto) {
        if (dto.history() == null) {
            return null;
        }

        var history = dto.history();
        
        if (history.lastUpdated() != null && history.lastUpdated().when() != null) {
            try {
                return Instant.parse(history.lastUpdated().when());
            } catch (DateTimeParseException e) {
                log.warn("Impossible de parser lastUpdated.when: {}", history.lastUpdated().when(), e);
            }
        }

        if (history.createdDate() != null) {
            try {
                return Instant.parse(history.createdDate());
            } catch (DateTimeParseException e) {
                log.warn("Impossible de parser createdDate: {}", history.createdDate(), e);
            }
        }

        return null;
    }
}
