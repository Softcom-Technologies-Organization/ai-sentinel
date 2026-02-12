package pro.softcom.aisentinel.infrastructure.confluence.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort.TestConfluenceConnectionCommand;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort.UpdateConfluenceConnectionCommand;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluenceConnectionConfigResponseDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.TestConfluenceConnectionRequestDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.UpdateConfluenceConnectionConfigRequestDto;

import java.util.concurrent.CompletableFuture;

/**
 * REST API endpoint for managing Confluence connection configuration.
 *
 * <p>Business purpose: Allows administrators to view and modify the Confluence
 * connection settings including URL, credentials, timeouts, and pagination parameters.
 * The API token is always masked in responses for security.
 */
@RestController
@RequestMapping("/api/v1/confluence/connection-config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Confluence Connection Config", description = "Manage Confluence connection configuration")
public class ConfluenceConnectionConfigController {

    public static final String ADMIN_USERNAME = "admin";
    private static final String MASKED_TOKEN = "***";

    private final ManageConfluenceConnectionPort manageConfluenceConnectionPort;

    /**
     * Retrieves the current Confluence connection configuration.
     * The API token is always masked in the response.
     *
     * @return Current connection configuration settings
     */
    @GetMapping
    @Operation(summary = "Get current Confluence connection configuration")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceConnectionConfigResponseDto>> getConfig() {
        log.debug("GET /api/v1/confluence/connection-config - Retrieving current configuration");

        return CompletableFuture.supplyAsync(() -> {
            try {
                ConfluenceConnectionSettings settings = manageConfluenceConnectionPort.getConnectionSettings();
                ConfluenceConnectionConfigResponseDto response = toResponseDto(settings);

                log.debug("Configuration retrieved successfully");
                return ResponseEntity.ok(response);

            } catch (Exception ex) {
                log.error("Failed to retrieve Confluence connection configuration: {}", ex.getMessage(), ex);
                return ResponseEntity.internalServerError().<ConfluenceConnectionConfigResponseDto>build();
            }
        });
    }

    /**
     * Updates the Confluence connection configuration.
     *
     * <p>Business rule: The API token is encrypted before storage and never returned in plaintext.
     *
     * @param request New connection configuration settings
     * @return Updated configuration (with masked token)
     */
    @PutMapping
    @Operation(summary = "Update Confluence connection configuration")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceConnectionConfigResponseDto>> updateConfig(
            @Valid @RequestBody UpdateConfluenceConnectionConfigRequestDto request) {

        log.info("PUT /api/v1/confluence/connection-config - Updating configuration: baseUrl={}, username={}",
                request.baseUrl(), request.username());

        return CompletableFuture.supplyAsync(() -> {
            try {
                String updatedBy = ADMIN_USERNAME;

                UpdateConfluenceConnectionCommand command = new UpdateConfluenceConnectionCommand(
                        request.baseUrl(),
                        request.username(),
                        request.apiToken(),
                        request.connectTimeout(),
                        request.readTimeout(),
                        request.maxRetries(),
                        request.pagesLimit(),
                        request.maxPages(),
                        updatedBy
                );

                ConfluenceConnectionSettings updatedSettings = manageConfluenceConnectionPort.updateConnectionSettings(command);
                ConfluenceConnectionConfigResponseDto response = toResponseDto(updatedSettings);

                log.info("Configuration updated successfully by user: {}", updatedBy);
                return ResponseEntity.ok(response);

            } catch (IllegalArgumentException ex) {
                log.warn("Invalid configuration request: {}", ex.getMessage());
                return ResponseEntity.badRequest().<ConfluenceConnectionConfigResponseDto>build();

            } catch (Exception ex) {
                log.error("Failed to update Confluence connection configuration: {}", ex.getMessage(), ex);
                return ResponseEntity.internalServerError().<ConfluenceConnectionConfigResponseDto>build();
            }
        });
    }

    /**
     * Tests the Confluence connection using the provided settings.
     *
     * @param request Connection settings to test
     * @return Test result with success/failure status
     */
    @PostMapping("/test")
    @Operation(summary = "Test Confluence connection")
    public CompletableFuture<ResponseEntity<@NonNull ConnectionTestResultDto>> testConnection(
            @Valid @RequestBody TestConfluenceConnectionRequestDto request) {

        log.info("POST /api/v1/confluence/connection-config/test - Testing connection to: {}", request.baseUrl());

        return CompletableFuture.supplyAsync(() -> {
            try {
                TestConfluenceConnectionCommand command = new TestConfluenceConnectionCommand(
                        request.baseUrl(),
                        request.username(),
                        request.apiToken()
                );

                boolean success = manageConfluenceConnectionPort.testConnection(command);
                String message = success
                        ? "Connection to Confluence established successfully"
                        : "Failed to connect to Confluence";

                return ResponseEntity.ok(new ConnectionTestResultDto(success, message));

            } catch (Exception ex) {
                log.error("Connection test failed: {}", ex.getMessage(), ex);
                return ResponseEntity.ok(new ConnectionTestResultDto(false, "Connection test failed: " + ex.getMessage()));
            }
        });
    }

    /**
     * Converts domain model to response DTO with masked token.
     */
    private ConfluenceConnectionConfigResponseDto toResponseDto(ConfluenceConnectionSettings settings) {
        return new ConfluenceConnectionConfigResponseDto(
                settings.baseUrl(),
                settings.username(),
                MASKED_TOKEN,
                settings.connectTimeout(),
                settings.readTimeout(),
                settings.maxRetries(),
                settings.pagesLimit(),
                settings.maxPages(),
                settings.updatedAt(),
                settings.updatedBy(),
                manageConfluenceConnectionPort.isConfigured()
        );
    }

    /**
     * DTO for connection test result.
     */
    public record ConnectionTestResultDto(boolean success, String message) {
    }
}
