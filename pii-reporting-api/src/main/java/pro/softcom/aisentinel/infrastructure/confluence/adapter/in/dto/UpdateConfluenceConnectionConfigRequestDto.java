package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;

/**
 * DTO for updating Confluence connection configuration via REST API.
 *
 * <p>Business purpose: Allows administrators to modify the Confluence connection
 * settings including URL, credentials, timeouts, and pagination parameters.
 *
 * @param baseUrl        Base URL of the Confluence instance
 * @param username       Username for Confluence authentication
 * @param apiToken       Plain-text API token (will be encrypted before storage)
 * @param connectTimeout Connection timeout in milliseconds
 * @param readTimeout    Read timeout in milliseconds
 * @param maxRetries     Maximum number of retry attempts
 * @param pagesLimit     Number of pages per pagination request
 * @param deploymentType Type of Confluence deployment ("CLOUD" or "DATA_CENTER")
 */
public record UpdateConfluenceConnectionConfigRequestDto(
        @JsonProperty("baseUrl")
        @NotBlank(message = "baseUrl is required")
        String baseUrl,

        @JsonProperty("username")
        String username,

        @JsonProperty("apiToken")
        String apiToken,

        @JsonProperty("connectTimeout")
        @NotNull(message = "connectTimeout is required")
        @Min(value = 1, message = "connectTimeout must be positive")
        Integer connectTimeout,

        @JsonProperty("readTimeout")
        @NotNull(message = "readTimeout is required")
        @Min(value = 1, message = "readTimeout must be positive")
        Integer readTimeout,

        @JsonProperty("maxRetries")
        @NotNull(message = "maxRetries is required")
        @Min(value = 0, message = "maxRetries cannot be negative")
        Integer maxRetries,

        @JsonProperty("pagesLimit")
        @NotNull(message = "pagesLimit is required")
        @Min(value = 1, message = "pagesLimit must be positive")
        Integer pagesLimit,

        @JsonProperty("deploymentType")
        @NotNull(message = "deploymentType is required")
        ConfluenceDeploymentType deploymentType
) {
}
