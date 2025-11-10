package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;

/**
 * Represents information about an attachment that was modified in Confluence.
 *
 * @param attachmentId unique identifier of the attachment
 * @param title        attachment name
 * @param lastModified last modification timestamp
 */
public record ModifiedAttachmentInfo(
    String attachmentId,
    String title,
    Instant lastModified
) {
}
