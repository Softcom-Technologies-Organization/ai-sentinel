package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort.UpdatePiiDetectionConfigCommand;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.PiiDetectionConfigResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.UpdatePiiDetectionConfigRequestDto;

/**
 * REST API endpoint for managing PII detection configuration.
 * 
 * <p>Business purpose: Allows administrators to view and modify the global PII detection
 * settings that control which detectors are active and their confidence thresholds.
 * These settings apply to all new scans initiated after the update.
 */
@RestController
@RequestMapping("/api/v1/pii-detection/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PII Detection Config", description = "Manage PII detection configuration")
public class PiiDetectionConfigController {

    public static final String ADMIN_USERNAME = "admin";
    private final ManagePiiDetectionConfigPort managePiiDetectionConfigPort;

    /**
     * Retrieves the current PII detection configuration.
     * 
     * @return Current configuration settings
     */
    @GetMapping
    @Operation(summary = "Get current PII detection configuration")
    public ResponseEntity<@NonNull PiiDetectionConfigResponseDto> getConfig() {
        log.debug("GET /api/v1/pii-detection/config - Retrieving current configuration");
        
        try {
            PiiDetectionConfig config = managePiiDetectionConfigPort.getConfig();
            PiiDetectionConfigResponseDto response = toResponseDto(config);
            
            log.debug("Configuration retrieved successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Failed to retrieve PII detection configuration: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Updates the PII detection configuration.
     * 
     * <p>Business rule: Configuration changes apply to all new scans started after the update.
     * Running scans continue with their original configuration.
     * 
     * @param request New configuration settings
     * @return Updated configuration
     */
    @PutMapping
    @Operation(summary = "Update PII detection configuration")
    public ResponseEntity<@NonNull PiiDetectionConfigResponseDto> updateConfig(
            @Valid @RequestBody UpdatePiiDetectionConfigRequestDto request) {
        
        log.info("PUT /api/v1/pii-detection/config - Updating configuration: gliner={}, " +
                "presidio={}, regex={}, threshold={}", 
                request.glinerEnabled(), request.presidioEnabled(), 
                request.regexEnabled(), request.defaultThreshold());
        
        try {
            String updatedBy = ADMIN_USERNAME;
            
            UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
                request.glinerEnabled(),
                request.presidioEnabled(),
                request.regexEnabled(),
                request.defaultThreshold(),
                request.nbOfLabelByPass(),
                updatedBy
            );
            
            PiiDetectionConfig updatedConfig = managePiiDetectionConfigPort.updateConfig(command);
            PiiDetectionConfigResponseDto response = toResponseDto(updatedConfig);
            
            log.info("Configuration updated successfully by user: {}", updatedBy);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid configuration request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (Exception ex) {
            log.error("Failed to update PII detection configuration: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Converts domain model to response DTO.
     */
    private PiiDetectionConfigResponseDto toResponseDto(PiiDetectionConfig config) {
        return new PiiDetectionConfigResponseDto(
            config.isGlinerEnabled(),
            config.isPresidioEnabled(),
            config.isRegexEnabled(),
            config.getDefaultThreshold(),
            config.getNbOfLabelByPass(),
            config.getUpdatedAt(),
            config.getUpdatedBy()
        );
    }
}