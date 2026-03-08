package pro.softcom.aisentinel.infrastructure.jira.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.softcom.aisentinel.application.jira.port.in.ManageJiraConnectionPort;
import pro.softcom.aisentinel.application.jira.port.in.ManageJiraConnectionPort.TestJiraConnectionCommand;
import pro.softcom.aisentinel.application.jira.port.in.ManageJiraConnectionPort.UpdateJiraConnectionCommand;
import pro.softcom.aisentinel.domain.jira.JiraConnectionSettings;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.JiraConnectionConfigResponseDto;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.TestJiraConnectionRequestDto;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.UpdateJiraConnectionConfigRequestDto;

import java.security.Principal;

/**
 * REST API endpoint for managing Jira connection configuration.
 *
 * <p>Business purpose: Allows administrators to view and modify the Jira
 * connection settings including URL, credentials, timeouts, and pagination parameters.
 * The API token is always masked in responses for security.
 */
@RestController
@RequestMapping("/api/v1/jira/connection-config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Jira Connection Config", description = "Manage Jira connection configuration")
public class JiraConnectionConfigController {

    private static final String MASKED_TOKEN = "***";
    private static final String SYSTEM_USER = "system";

    private final ManageJiraConnectionPort manageJiraConnectionPort;

    @GetMapping
    @Operation(summary = "Get current Jira connection configuration")
    public ResponseEntity<@NonNull JiraConnectionConfigResponseDto> getConfig() {
        log.debug("GET /api/v1/jira/connection-config - Retrieving current configuration");

        try {
            JiraConnectionSettings settings = manageJiraConnectionPort.getConnectionSettings();
            JiraConnectionConfigResponseDto response = toResponseDto(settings);

            log.debug("Configuration retrieved successfully");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Failed to retrieve Jira connection configuration: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping
    @Operation(summary = "Update Jira connection configuration")
    public ResponseEntity<@NonNull JiraConnectionConfigResponseDto> updateConfig(
            @Valid @RequestBody UpdateJiraConnectionConfigRequestDto request,
            Principal principal) {

        String updatedBy = principal != null ? principal.getName() : SYSTEM_USER;
        log.info("PUT /api/v1/jira/connection-config - Updating configuration by user: {}", updatedBy);

        try {
            UpdateJiraConnectionCommand command = new UpdateJiraConnectionCommand(
                    request.baseUrl(),
                    request.email(),
                    request.apiToken(),
                    request.connectTimeout(),
                    request.readTimeout(),
                    request.maxRetries(),
                    request.issuesLimit(),
                    request.maxIssues(),
                    request.deploymentType(),
                    updatedBy
            );

            JiraConnectionSettings updatedSettings = manageJiraConnectionPort.updateConnectionSettings(command);
            JiraConnectionConfigResponseDto response = toResponseDto(updatedSettings);

            log.info("Configuration updated successfully by user: {}", updatedBy);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid configuration request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception ex) {
            log.error("Failed to update Jira connection configuration: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/test")
    @Operation(summary = "Test Jira connection")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<@NonNull ConnectionTestResultDto> testConnection(
            @Valid @RequestBody TestJiraConnectionRequestDto request) {

        log.info("POST /api/v1/jira/connection-config/test - Testing connection");

        try {
            TestJiraConnectionCommand command = new TestJiraConnectionCommand(
                    request.baseUrl(),
                    request.email(),
                    request.apiToken(),
                    request.deploymentType()
            );

            boolean success = manageJiraConnectionPort.testConnection(command);
            String message = success
                    ? "Connection to Jira established successfully"
                    : "Failed to connect to Jira";

            return ResponseEntity.ok(new ConnectionTestResultDto(success, message));

        } catch (Exception ex) {
            log.error("Connection test failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ConnectionTestResultDto(false, "Connection test failed: " + ex.getMessage()));
        }
    }

    private JiraConnectionConfigResponseDto toResponseDto(JiraConnectionSettings settings) {
        return new JiraConnectionConfigResponseDto(
                settings.baseUrl(),
                settings.email(),
                MASKED_TOKEN,
                settings.connectTimeout(),
                settings.readTimeout(),
                settings.maxRetries(),
                settings.issuesLimit(),
                settings.maxIssues(),
                settings.deploymentType(),
                settings.updatedAt(),
                settings.updatedBy(),
                manageJiraConnectionPort.isConfigured()
        );
    }

    public record ConnectionTestResultDto(boolean success, String message) {
    }
}
