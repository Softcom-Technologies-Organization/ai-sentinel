package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import lombok.Builder;

import java.util.List;

/**
 * DTO de réponse pour une recherche de pages Confluence.
 */
@Builder
public record ConfluenceSearchResponseDto(
    List<ConfluencePageDto> results,
    int totalResults,
    String query
) {}
