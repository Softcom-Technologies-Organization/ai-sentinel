package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper;

import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluencePageDto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
        int nextVersion = metadata != null ? metadata.version() : 1;
        // Only version.number must be sent on update; Confluence assigns version.by and version.when
        // server-side. Sending them is unnecessary and unsafe: the domain keeps 'when' as an
        // offset-less LocalDateTime, and Confluence Cloud rejects an offset-less timestamp with
        // 400 "Invalid format ... is too short" (Version[when]), which broke every redaction PUT.
        return new ConfluencePageDto.VersionDto(null, null, nextVersion, false, null);
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
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception _) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
    }

}
