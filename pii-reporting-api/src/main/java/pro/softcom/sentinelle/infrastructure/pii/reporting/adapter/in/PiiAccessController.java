package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.softcom.sentinelle.application.pii.reporting.config.PiiReportingProperties;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.List;

/**
 * REST controller for PII data access control.
 * Business intent: provide controlled access to decrypted PII data with audit trail.
 */
@RestController
@RequestMapping("/api/v1/pii")
@Tag(name = "PII Access Control", description = "Control of access to sensitive PII data")
@RequiredArgsConstructor
@Slf4j
public class PiiAccessController {

    private final PiiReportingProperties reportingProperties;
    private final ScanResultQuery scanResultQuery;

    @GetMapping("/config/reveal-allowed")
    @Operation(summary = "Checks if secret revelation is allowed")
    @ApiResponse(responseCode = "200", description = "Configuration returned")
    public ResponseEntity<@NonNull Boolean> isRevealAllowed() {
        return ResponseEntity.ok(reportingProperties.isAllowSecretReveal());
    }

    @PostMapping("/reveal-page")
    @Operation(summary = "Reveals PII secrets from a Confluence page")
    @ApiResponse(responseCode = "200", description = "Secrets successfully revealed")
    @ApiResponse(responseCode = "403", description = "Revelation not authorized by configuration")
    @ApiResponse(responseCode = "404", description = "Page not found")
    public ResponseEntity<@NonNull PageSecretsResponse> revealPageSecrets(
            @RequestBody PageRevealRequest request
    ) {
        // Check configuration
        if (!reportingProperties.isAllowSecretReveal()) {
            log.warn("[PII_ACCESS] Reveal attempt denied by configuration for pageId={}", request.pageId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("[PII_ACCESS] Reveal request for pageId={}", request.pageId());

        // Query with automatic decryption (AccessPurpose.USER_DISPLAY)
        List<ScanResult> results = scanResultQuery.listItemEventsDecrypted(
                request.scanId(),
                request.pageId(),
                AccessPurpose.USER_DISPLAY
        );

        if (results.isEmpty()) {
            log.warn("[PII_ACCESS] No results found for pageId={}", request.pageId());
            return ResponseEntity.notFound().build();
        }

        // Take the first result (should be unique per pageId)
        ScanResult result = results.getFirst();

        // Extract decrypted secrets
        List<RevealedSecret> secrets = result.detectedEntities().stream()
                .map(e -> new RevealedSecret(
                        e.startPosition(),
                        e.endPosition(),
                        e.sensitiveValue(),
                        e.sensitiveContext(),
                        e.maskedContext()
                ))
                .toList();

        log.info("[PII_ACCESS] Revealed {} secrets for pageId={} (scanId={})",
                secrets.size(), result.pageId(), result.scanId());

        return ResponseEntity.ok(new PageSecretsResponse(
                result.scanId(),
                result.pageId(),
                result.pageTitle(),
                secrets
        ));
    }

    /**
     * Request DTO for page secret revelation.
     */
    public record PageRevealRequest(String scanId, String pageId) {}

    /**
     * Response DTO containing revealed secrets for a page.
     */
    public record PageSecretsResponse(
            String spaceKey,
            String pageId,
            String pageTitle,
            List<RevealedSecret> secrets
    ) {}

    /**
     * DTO for a single revealed secret with position information.
     */
    public record RevealedSecret(
            int startPosition,
            int endPosition,
            String sensitiveValue,
            String sensitiveContext,
            String maskedContext
    ) {}
}
