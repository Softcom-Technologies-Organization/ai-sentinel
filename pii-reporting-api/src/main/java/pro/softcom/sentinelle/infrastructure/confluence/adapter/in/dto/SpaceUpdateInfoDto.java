package pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * DTO representing update information for a Confluence space.
 * 
 * Business purpose: Inform the frontend whether a space has been modified
 * since its last scan, enabling visual indicators for spaces that may need re-scanning.
 */
public record SpaceUpdateInfoDto(
    String spaceKey,
    String spaceName,
    boolean hasBeenUpdated,
    @Nullable Instant lastModified,
    @Nullable Instant lastScanDate,
    @Nullable List<String> updatedPages,
    @Nullable List<String> updatedAttachments
) {
}
