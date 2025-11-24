package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluencePageDto;

public final class ConfluencePageMapper {

    private ConfluencePageMapper() {
        // utility class
    }

    public static ConfluencePage toDomain(ConfluencePageDto dto) {
        if (dto == null) return null;

        var pageContent = buildContent(dto);
        var pageMetadata = buildMetadata(dto);
        var spaceKey = extractSpaceKey(dto);
        Map<String, Object> properties = dto.metadata() != null ? dto.metadata() : Map.of();

        return new ConfluencePage(
            dto.id(),
            dto.title(),
            spaceKey,
            pageContent,
            pageMetadata,
            extractLabels(),
            properties
        );
    }

    /**
     * Transforme la page de domaine vers son DTO Confluence.
     * Retourne null si l'entrée est null.
     */
    public static ConfluencePageDto fromDomain(ConfluencePage page) {
        if (page == null) return null;

        var spaceDto = new ConfluencePageDto.SpaceDto(page.spaceKey(), null);
        var bodyDto = buildBodyDto(page);
        var versionDto = buildVersionDto(page);
        var status = page.metadata() != null ? page.metadata().status() : "current";

        return new ConfluencePageDto(
            page.id(),
            "page",
            status,
            page.title(),
            spaceDto,
            bodyDto,
            versionDto,
            page.customProperties(),
            null
        );
    }

    private static ConfluencePageDto.BodyDto buildBodyDto(ConfluencePage page) {
        var content = page.content();
        var contentValue = content != null ? content.body() : "";
        var representation = content != null ? content.format() : "storage";
        var contentDto = new ConfluencePageDto.ContentDto(contentValue, representation);
        return new ConfluencePageDto.BodyDto(contentDto, null);
    }

    private static ConfluencePageDto.VersionDto buildVersionDto(ConfluencePage page) {
        var metadata = page.metadata();
        if (metadata == null) {
            return new ConfluencePageDto.VersionDto(null, formatDateTime(LocalDateTime.now()), 1, false, null);
        }
        var userDto = new ConfluencePageDto.UserDto("known", metadata.lastModifiedBy(), null, null);
        return new ConfluencePageDto.VersionDto(
            userDto,
            formatDateTime(metadata.lastModifiedDate()),
            metadata.version(),
            false,
            null
        );
    }

    private static ConfluencePage.PageContent buildContent(ConfluencePageDto dto) {
        var body = dto.body();
        var storage = body != null ? body.storage() : null;
        var value = storage != null ? storage.value() : "";
        return new ConfluencePage.HtmlContent(value);
    }

    private static ConfluencePage.PageMetadata buildMetadata(ConfluencePageDto dto) {
        var version = dto.version();
        if (version == null) return null;
        var by = version.by();
        String user = by != null ? by.username() : "unknown";
        var when = parseDateTime(version.when());
        String status = dto.status() != null ? dto.status() : "current";
        return new ConfluencePage.PageMetadata(user, when, user, when, version.number(), status);
    }

    private static String extractSpaceKey(ConfluencePageDto dto) {
        var space = dto.space();
        return space != null ? space.key() : "";
    }

    private static List<String> extractLabels() {
        // Labels not available in current metadata model; intentionally returns empty list.
        // Tracked to be enabled when metadata exposes labels (see backlog ticket SNT-Labels-Enable).
        return List.of();
    }

    private static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception _) {
            return LocalDateTime.now();
        }
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null
            ? dateTime.format(DateTimeFormatter.ISO_DATE_TIME)
            : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
