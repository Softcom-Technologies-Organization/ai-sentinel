package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.RevealPiiSecretsUseCase;

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

    private final RevealPiiSecretsUseCase revealPiiSecretsUseCase;
    private final PageSecretsResponseMapper mapper;

    @GetMapping("/config/reveal-allowed")
    @Operation(summary = "Checks if secret revelation is allowed")
    @ApiResponse(responseCode = "200", description = "Configuration returned")
    public ResponseEntity<@NonNull Boolean> isRevealAllowed() {
        return ResponseEntity.ok(revealPiiSecretsUseCase.isRevealAllowed());
    }

    @PostMapping("/reveal-page")
    @Operation(summary = "Reveals PII secrets from a Confluence page")
    @ApiResponse(responseCode = "200", description = "Secrets successfully revealed")
    @ApiResponse(responseCode = "403", description = "Revelation not authorized by configuration")
    @ApiResponse(responseCode = "404", description = "Page not found")
    public ResponseEntity<@NonNull PageSecretsResponseDto> revealPageSecrets(
            @RequestBody PageRevealRequest request
    ) {
        try {
            return revealPiiSecretsUseCase.revealPageSecrets(request.scanId(), request.pageId())
                    .map(response -> ResponseEntity.ok(mapper.toDto(response)))
                    .orElseGet(() -> {
                        log.warn("[PII_ACCESS] No results found for pageId={}", request.pageId());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    });
        } catch (SecurityException e) {
            log.warn("[PII_ACCESS] Reveal attempt denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Request DTO for page secret revelation.
     */
    public record PageRevealRequest(String scanId, String pageId) {}

    /**
     * Response DTO containing revealed secrets for a page.
     */
    public record PageSecretsResponseDto(
            String scanId,
            String pageId,
            String pageTitle,
            List<RevealedSecretDto> secrets
    ) {}

    /**
     * DTO for a single revealed secret with position information.
     */
    public record RevealedSecretDto(
            int startPosition,
            int endPosition,
            String sensitiveValue,
            String sensitiveContext,
            String maskedContext
    ) {}
}
