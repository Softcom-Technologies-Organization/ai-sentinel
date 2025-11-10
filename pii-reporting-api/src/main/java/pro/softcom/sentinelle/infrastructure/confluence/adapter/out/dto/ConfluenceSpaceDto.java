package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * DTO pour la sérialisation/désérialisation des espaces Confluence. Compatible avec l'API REST de
 * Confluence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSpaceDto(
    String id,
    String key,
    String name,
    String type,
    String status,
    DescriptionDto description,
    List<PermissionDto> permissions,
    Map<String, Object> metadata,
    @JsonProperty("_links") Map<String, String> links,
    HistoryDto history
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DescriptionDto(
        PlainTextDto plain,
        ViewDto view
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlainTextDto(
        String value,
        String representation
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViewDto(
        String value,
        String representation
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionDto(
        SubjectsDto subjects,
        OperationDto operation
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubjectsDto(
        UserResultsDto user,
        GroupResultsDto group
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserResultsDto(
        List<UserDto> results,
        int size
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupResultsDto(
        List<GroupDto> results,
        int size
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(
        @JsonProperty("type") String type,
        @JsonProperty("accountId") String accountId,
        @JsonProperty("accountType") String accountType,
        @JsonProperty("accountStatus") String accountStatus,
        @JsonProperty("email") String email,
        @JsonProperty("publicName") String publicName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroupDto(
        String type,
        String name
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationDto(
        String operation,
        String targetType
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryDto(
        String createdDate,
        LastUpdatedDto lastUpdated
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastUpdatedDto(
        String when,
        UserDto by
    ) {

    }
}
