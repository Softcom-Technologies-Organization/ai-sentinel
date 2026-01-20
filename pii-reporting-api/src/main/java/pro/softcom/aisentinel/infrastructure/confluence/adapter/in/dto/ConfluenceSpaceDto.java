package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import lombok.Builder;

/**
 * API DTO représentant un espace Confluence exposé par le contrat HTTP.
 */
@Builder
public record ConfluenceSpaceDto(
    String id,
    String key,
    String name,
    String url,
    String description,
    String type,
    String status
) {}
