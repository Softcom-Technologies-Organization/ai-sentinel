package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for testing Confluence connection via REST API.
 *
 * <p>Only requires connection credentials, not the full configuration.
 *
 * @param baseUrl  Base URL of the Confluence instance
 * @param username Username for Confluence authentication
 * @param apiToken Plain-text API token for authentication
 */
public record TestConfluenceConnectionRequestDto(
        @JsonProperty("baseUrl")
        @NotBlank(message = "baseUrl is required")
        String baseUrl,

        @JsonProperty("username")
        @NotBlank(message = "username is required")
        String username,

        @JsonProperty("apiToken")
        @NotBlank(message = "apiToken is required")
        String apiToken
) {
}
