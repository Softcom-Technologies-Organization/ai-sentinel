package pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto;

import java.util.List;
import lombok.Builder;

/**
 * DTO de réponse pour une recherche de pages Confluence.
 */
@Builder
public record ConfluenceSearchResponseDto(
    List<ConfluencePageDto> results,
    int totalResults,
    String query
) {}
