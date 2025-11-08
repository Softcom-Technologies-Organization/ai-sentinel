package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper;

import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.DataOwners;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;

/**
 * Maps ConfluenceSpaceDto (REST) to ConfluenceSpace (domain).
 * URL is presentation-facing; by default, we keep it null here to avoid leaking UI concerns.
 * An overloaded method allows providing a ConfluenceUrlBuilder to populate the URL when required.
 */
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
            throw new IllegalArgumentException("ConfluenceSpaceDto ne peut pas être null");
        }
        var spaceType = parseSpaceType(dto.type());
        var spaceStatus = parseSpaceStatus(dto.status());
        var descriptionText = extractDescription(dto);
        var url = ConfluenceUrlBuilder.spaceOverviewUrl(dto.key());
        var confluenceSpaceDataOwners = ConfluenceDataOwnerMapper.extractDataOwners(dto);
        var dataOwners = new DataOwners.Loaded(confluenceSpaceDataOwners);

        return new ConfluenceSpace(
            dto.id(),
            dto.key(),
            dto.name(),
            url,
            descriptionText,
            spaceType,
            spaceStatus,
            dataOwners
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

}
