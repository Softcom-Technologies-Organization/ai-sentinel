package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;

/**
 * Représente les informations d'une pièce jointe modifiée dans Confluence.
 *
 * @param attachmentId ID unique de la pièce jointe
 * @param title        Nom de la pièce jointe
 * @param lastModified Date de dernière modification
 */
public record ModifiedAttachmentInfo(
    String attachmentId,
    String title,
    Instant lastModified
) {
}
