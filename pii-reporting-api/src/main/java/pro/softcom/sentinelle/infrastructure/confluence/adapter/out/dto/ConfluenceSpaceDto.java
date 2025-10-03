package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * DTO pour la sérialisation/désérialisation des espaces Confluence.
 * Compatible avec l'API REST de Confluence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSpaceDto(
    @JsonProperty("id") String id,
    @JsonProperty("key") String key,
    @JsonProperty("name") String name,
    @JsonProperty("type") String type,
    @JsonProperty("status") String status,
    @JsonProperty("description") DescriptionDto description,
    @JsonProperty("permissions") List<PermissionDto> permissions,
    @JsonProperty("metadata") Map<String, Object> metadata,
    @JsonProperty("_links") Map<String, String> links
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DescriptionDto(
        @JsonProperty("plain") PlainTextDto plain,
        @JsonProperty("view") ViewDto view
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlainTextDto(
        @JsonProperty("value") String value,
        @JsonProperty("representation") String representation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViewDto(
        @JsonProperty("value") String value,
        @JsonProperty("representation") String representation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionDto(
        @JsonProperty("subjects") SubjectsDto subjects,
        @JsonProperty("operation") OperationDto operation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubjectsDto(
        @JsonProperty("user") UserResultsDto user,
        @JsonProperty("group") GroupResultsDto group
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserResultsDto(
        @JsonProperty("results") List<UserDto> results,
        @JsonProperty("size") int size
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupResultsDto(
        @JsonProperty("results") List<GroupDto> results,
        @JsonProperty("size") int size
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(
        @JsonProperty("type") String type,
        @JsonProperty("username") String username,
        @JsonProperty("userKey") String userKey,
        @JsonProperty("displayName") String displayName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupDto(
        @JsonProperty("type") String type,
        @JsonProperty("name") String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationDto(
        @JsonProperty("operation") String operation,
        @JsonProperty("targetType") String targetType
    ) {}
}
