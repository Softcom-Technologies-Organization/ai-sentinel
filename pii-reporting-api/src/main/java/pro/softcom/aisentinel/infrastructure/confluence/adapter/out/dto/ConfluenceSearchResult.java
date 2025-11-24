package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Résultat de recherche Confluence
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSearchResult(
    @JsonProperty("results") List<ConfluencePageDto> results,
    @JsonProperty("start") int start,
    @JsonProperty("limit") int limit,
    @JsonProperty("size") int size,
    @JsonProperty("_links") Map<String, String> links
) {}
