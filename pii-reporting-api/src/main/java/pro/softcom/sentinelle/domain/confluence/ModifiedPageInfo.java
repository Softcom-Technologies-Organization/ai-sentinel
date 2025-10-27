package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;

/**
 * Représente les informations d'une page modifiée dans Confluence.
 * 
 * @param pageId ID unique de la page
 * @param title Titre de la page
 * @param lastModified Date de dernière modification
 */
public record ModifiedPageInfo(
    String pageId,
    String title,
    Instant lastModified
) {
}
