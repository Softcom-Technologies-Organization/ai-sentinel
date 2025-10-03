package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PurgeDetectionDataUseCase;

/**
 * Command endpoint to purge all persisted data from previous scans.
 * Business rule: called right before starting a new multi-space scan to start from a clean slate.
 */
@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scan Admin", description = "Administrative operations for scan lifecycle")
public class ScanPurgeController {

    private final PurgeDetectionDataUseCase purgeDetectionDataUseCase;

    /**
     * Deletes all scan-related persisted data (events and checkpoints).
     * Kept minimal and atomic for simplicity.
     */
    @PostMapping("/purge")
    @Operation(summary = "Purge all previous scan data")
    @Transactional
    public ResponseEntity<@NonNull Void> purgeAll() {
        log.info("[PURGE] Deleting all scan_events and scan_checkpoints before starting a new scan");
        try {
            purgeDetectionDataUseCase.purgeAll();
            return ResponseEntity.accepted().build();
        } catch (RuntimeException ex) {
            log.error("[PURGE] Failed to purge previous scan data: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}
