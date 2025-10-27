package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Value object representing update information for a Confluence space.
 * 
 * Business purpose: Provide information about whether a space has been updated
 * since the last scan, and if so, which pages or attachments have changed.
 * 
 * @param spaceKey The unique key of the Confluence space
 * @param spaceName The display name of the space
 * @param hasBeenUpdated Whether the space has been updated since the last scan
 * @param lastModified The date of the last modification in the space (from pages)
 * @param lastScanDate The date of the last scan performed on this space
 * @param updatedPages List of page titles that have been updated (may be null if not calculated)
 * @param updatedAttachments List of attachment names that have been updated (may be null if not calculated)
 */
public record SpaceUpdateInfo(
    String spaceKey,
    String spaceName,
    boolean hasBeenUpdated,
    @Nullable Instant lastModified,
    @Nullable Instant lastScanDate,
    @Nullable List<String> updatedPages,
    @Nullable List<String> updatedAttachments
) {
    
    /**
     * Creates a SpaceUpdateInfo indicating no scan has been performed yet.
     */
    public static SpaceUpdateInfo noScanYet(String spaceKey, String spaceName, @Nullable Instant lastModified) {
        return new SpaceUpdateInfo(
            spaceKey,
            spaceName,
            false,
            lastModified,
            null,
            null,
            null
        );
    }
    
    /**
     * Creates a SpaceUpdateInfo indicating no updates since last scan.
     */
    public static SpaceUpdateInfo noUpdates(
            String spaceKey, 
            String spaceName, 
            @Nullable Instant lastModified, 
            Instant lastScanDate) {
        return new SpaceUpdateInfo(
            spaceKey,
            spaceName,
            false,
            lastModified,
            lastScanDate,
            null,
            null
        );
    }
    
    /**
     * Creates a SpaceUpdateInfo indicating updates have been detected.
     */
    public static SpaceUpdateInfo withUpdates(
            String spaceKey,
            String spaceName,
            Instant lastModified,
            Instant lastScanDate,
            @Nullable List<String> updatedPages,
            @Nullable List<String> updatedAttachments) {
        return new SpaceUpdateInfo(
            spaceKey,
            spaceName,
            true,
            lastModified,
            lastScanDate,
            updatedPages,
            updatedAttachments
        );
    }
}
