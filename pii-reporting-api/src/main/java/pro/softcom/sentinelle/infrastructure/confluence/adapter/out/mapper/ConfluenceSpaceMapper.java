package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto.HistoryDto;

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

    private static Instant extractLastModified(ConfluenceSpaceDto dto) {
        var history = dto.history();

        Instant lastModified = null;

        if (history != null) {
            if (hasHistoryBeenUpdated(history)) {
                lastModified = parseInstantSafely(history.lastUpdated().when());
            }

            if (history.createdDate() != null) {
                lastModified = parseInstantSafely(history.createdDate());
            }
        }

        return lastModified;
    }

    private static boolean hasHistoryBeenUpdated(HistoryDto history) {
        return history.lastUpdated() != null && history.lastUpdated().when() != null;
    }

    private static Instant parseInstantSafely(String dateString) {
        try {
            return Instant.parse(dateString);
        } catch (DateTimeParseException e) {
            log.error("Cannot parse {} to Instant", dateString, e);
            return null;
        }
    }
}
