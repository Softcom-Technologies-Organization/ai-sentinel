package pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto;

import java.util.List;
import lombok.Builder;

/**
 * API DTO représentant une page Confluence exposée par le contrat HTTP.
 * Ne contient que les champs nécessaires côté client et ne fuit pas les détails du domaine.
 */
@Builder
public record ConfluencePageDto(
    String id,
    String title,
    String spaceKey,
    String content,
    String contentFormat,
    List<String> labels
) {}
