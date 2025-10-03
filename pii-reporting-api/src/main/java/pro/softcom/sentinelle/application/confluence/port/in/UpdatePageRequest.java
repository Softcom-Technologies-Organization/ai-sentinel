package pro.softcom.sentinelle.application.confluence.port.in;

import java.util.List;
import lombok.Builder;

/**
 * Commande d'application pour mettre à jour une page Confluence.
 * Porte uniquement les informations nécessaires à l'opération, sans détails techniques HTTP.
 */
@Builder
public record UpdatePageRequest(
    String pageId,
    String title,
    String content,
    List<String> labels
) {}
