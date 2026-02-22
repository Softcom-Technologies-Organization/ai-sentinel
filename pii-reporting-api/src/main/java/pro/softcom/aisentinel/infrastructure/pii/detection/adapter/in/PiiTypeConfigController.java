package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.CategoryGroupResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.CreatePiiTypeConfigRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.GroupedPiiTypesResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.PiiTypeConfigResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.UpdatePiiTypeConfigRequestDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing PII type-specific configurations.
 * <p>
 * Provides endpoints to:
 * - List all PII type configurations
 * - Get configurations by detector
 * - Get configurations grouped by category
 * - Update individual configuration
 * - Bulk update configurations
 */
@RestController
@RequestMapping("/api/v1/pii-detection/pii-types")
public class PiiTypeConfigController {

    private final ManagePiiTypeConfigsPort managePiiTypeConfigsPort;

    private static final String PLACEHOLDER_USER = "admin";

    public PiiTypeConfigController(ManagePiiTypeConfigsPort managePiiTypeConfigsPort) {
        this.managePiiTypeConfigsPort = managePiiTypeConfigsPort;
    }

    /**
     * Get all PII type configurations.
     * <p>
     * GET /api/v1/pii-detection/types
     *
     * @return list of all PII type configurations
     */
    @GetMapping
    public ResponseEntity<@NonNull List<PiiTypeConfigResponseDto>> getAllConfigs() {
        List<PiiTypeConfig> configs = managePiiTypeConfigsPort.getAllConfigs();
        List<PiiTypeConfigResponseDto> response = configs.stream()
                .map(PiiTypeConfigResponseDto::fromDomain)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new custom PII type configuration.
     * <p>
     * POST /api/v1/pii-detection/pii-types
     *
     * @param request the creation request
     * @return the created configuration with HTTP 201
     */
    @PostMapping
    public ResponseEntity<@NonNull PiiTypeConfigResponseDto> createConfig(
            @Valid @RequestBody CreatePiiTypeConfigRequestDto request
    ) {
        var command = new ManagePiiTypeConfigsPort.CreatePiiTypeConfigCommand(
                request.piiType(),
                request.detector(),
                request.enabled(),
                request.threshold(),
                request.category(),
                request.detectorLabel(),
                request.countryCode(),
                request.severity(),
                PLACEHOLDER_USER
        );
        PiiTypeConfig created = managePiiTypeConfigsPort.createConfig(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PiiTypeConfigResponseDto.fromDomain(created));
    }

    /**
     * Get PII type configurations for a specific detector.
     * <p>
     * GET /api/v1/pii-detection/types/{detector}
     *
     * @param detector the detector name (GLINER, PRESIDIO, or REGEX)
     * @return list of configurations for the detector
     */
    @GetMapping("/{detector}")
    public ResponseEntity<@NonNull List<PiiTypeConfigResponseDto>> getConfigsByDetector(
            @PathVariable String detector
    ) {
        List<PiiTypeConfig> configs = managePiiTypeConfigsPort.getConfigsByDetector(detector);
        List<PiiTypeConfigResponseDto> response = configs.stream()
                .map(PiiTypeConfigResponseDto::fromDomain)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Get PII type configurations grouped by category.
     * <p>
     * GET /api/v1/pii-detection/types/grouped/by-category
     *
     * @return map of category to list of configurations
     */
    @GetMapping("/grouped/by-category")
    public ResponseEntity<@NonNull Map<String, List<PiiTypeConfigResponseDto>>> getConfigsByCategory() {
        Map<String, List<PiiTypeConfig>> configsByCategory =
                managePiiTypeConfigsPort.getConfigsByCategory();

        Map<String, List<PiiTypeConfigResponseDto>> response = configsByCategory.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(PiiTypeConfigResponseDto::fromDomain)
                                .toList()
                ));

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a custom PII type configuration.
     * <p>
     * DELETE /api/v1/pii-detection/pii-types/{detector}/{piiType}
     *
     * @param detector the detector name
     * @param piiType  the PII type identifier
     * @return 204 No Content on success, 409 Conflict if system type
     */
    @DeleteMapping("/{detector}/{piiType}")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable String detector,
            @PathVariable String piiType
    ) {
        managePiiTypeConfigsPort.deleteConfig(piiType, detector);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update a specific PII type configuration.
     * <p>
     * PUT /api/v1/pii-detection/types/{detector}/{piiType}
     *
     * @param detector the detector name
     * @param piiType  the PII type identifier
     * @param request  the update request
     * @return the updated configuration
     */
    @PutMapping("/{detector}/{piiType}")
    public ResponseEntity<@NonNull PiiTypeConfigResponseDto> updateConfig(
            @PathVariable String detector,
            @PathVariable String piiType,
            @Valid @RequestBody UpdatePiiTypeConfigRequestDto request
    ) {
        // Validate path variables match request body
        if (!detector.equals(request.detector()) || !piiType.equals(request.piiType())) {
            throw new IllegalArgumentException(
                    "Path parameters must match request body values"
            );
        }

        PiiTypeConfig updated = managePiiTypeConfigsPort.updateConfig(
                request.piiType(),
                request.detector(),
                request.enabled(),
                request.threshold(),
                PLACEHOLDER_USER
        );

        return ResponseEntity.ok(PiiTypeConfigResponseDto.fromDomain(updated));
    }

    /**
     * Bulk update multiple PII type configurations.
     * <p>
     * PUT /api/v1/pii-detection/pii-types/bulk
     *
     * @param requests list of update requests
     * @return list of updated configurations
     */
    @PutMapping("/bulk")
    public ResponseEntity<@NonNull List<PiiTypeConfigResponseDto>> bulkUpdate(
            @Valid @RequestBody List<UpdatePiiTypeConfigRequestDto> requests
    ) {
        List<ManagePiiTypeConfigsPort.PiiTypeConfigUpdate> updates = requests.stream()
                .map(req -> new ManagePiiTypeConfigsPort.PiiTypeConfigUpdate(
                        req.piiType(),
                        req.detector(),
                        req.enabled(),
                        req.threshold()
                ))
                .toList();

        List<PiiTypeConfig> updated = managePiiTypeConfigsPort.bulkUpdate(
                updates,
                PLACEHOLDER_USER
        );

        List<PiiTypeConfigResponseDto> response = updated.stream()
                .map(PiiTypeConfigResponseDto::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get PII type configurations grouped by detector and category for UI display.
     * Returns a nested structure: detector → categories → types.
     * Only includes GLINER and PRESIDIO detectors (REGEX excluded).
     * <p>
     * GET /api/v1/pii-detection/pii-types/grouped
     *
     * @return list of grouped configurations by detector and category
     */
    @GetMapping("/grouped")
    public ResponseEntity<@NonNull List<@NonNull GroupedPiiTypesResponseDto>> getGroupedForUI() {
        List<PiiTypeConfig> allConfigs = managePiiTypeConfigsPort.getAllConfigs();

        // Group by detector, then by category
        Map<String, Map<String, List<PiiTypeConfig>>> groupedByDetectorAndCategory = allConfigs.stream()
                .filter(config -> !"REGEX".equals(config.getDetector())) // Exclude REGEX
                .collect(Collectors.groupingBy(
                        PiiTypeConfig::getDetector,
                        Collectors.groupingBy(PiiTypeConfig::getCategory)
                ));

        // Convert to response DTOs
        List<GroupedPiiTypesResponseDto> response = groupedByDetectorAndCategory.entrySet().stream()
                .map(detectorEntry -> {
                    String detector = detectorEntry.getKey();
                    Map<String, List<PiiTypeConfig>> categoriesMap = detectorEntry.getValue();

                    List<CategoryGroupResponseDto> categories = categoriesMap.entrySet().stream()
                            .map(categoryEntry -> new CategoryGroupResponseDto(
                                    categoryEntry.getKey(),
                                    categoryEntry.getValue().stream()
                                            .map(PiiTypeConfigResponseDto::fromDomain)
                                            .toList()
                            ))
                            .sorted(Comparator.comparing(CategoryGroupResponseDto::category))
                            .toList();

                    return new GroupedPiiTypesResponseDto(detector, categories);
                })
                .sorted(Comparator.comparing(GroupedPiiTypesResponseDto::detector))
                .toList();

        return ResponseEntity.ok(response);
    }
}
