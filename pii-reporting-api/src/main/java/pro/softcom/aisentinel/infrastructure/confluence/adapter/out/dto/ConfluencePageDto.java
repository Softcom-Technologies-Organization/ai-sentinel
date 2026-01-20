package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

/**
 * DTO pour la sérialisation/désérialisation des pages Confluence.
 * Compatible avec l'API REST de Confluence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record ConfluencePageDto(
    String id,
    String type,
    String status,
    String title,
    SpaceDto space,
    BodyDto body,
    VersionDto version,
    Map<String, Object> metadata,
    @JsonProperty("_links") Map<String, String> links
) {
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    public record SpaceDto(
        String key,
        String name
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BodyDto(
        ContentDto storage,
        ContentDto view
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDto(
        String value,
        String representation
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VersionDto(
        UserDto by,
        String when,
        int number,
        boolean minorEdit,
        String message
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(
        String type,
        String username,
        String userKey,
        String displayName
    ) {}
}
