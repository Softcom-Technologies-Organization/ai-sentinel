package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO pour la réponse de l'API Confluence lors de la récupération de tous les espaces.
 * Cette classe mappe la structure JSON retournée par l'endpoint /space de l'API Confluence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSpacesResponseDto(
        @JsonProperty("results") List<ConfluenceSpaceDto> results,
        @JsonProperty("size") int size,
        @JsonProperty("limit") int limit,
        @JsonProperty("start") int start,
        @JsonProperty("_links") Links links
) {
    /**
     * Liens de pagination renvoyés par l'API Confluence.
     * Contient notamment le lien "next" pour indiquer la page suivante.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(@JsonProperty("next") String next) {}
}
