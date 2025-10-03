package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;

/**
 * Command endpoint to resume an interrupted scan using the same scanId.
 * Business rule: uses checkpoints to skip already processed spaces/pages.
 * Important: This endpoint no longer starts the resume job by itself. The UI attaches
 * to the SSE endpoint with the same scanId, which drives the resume execution.
 */
@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
public class ResumeScanController {

    private final StreamConfluenceResumeScanUseCase streamConfluenceScanUseCase;

    @PostMapping("/{scanId}/resume")
    public ResponseEntity<@NonNull Void> resume(@PathVariable String scanId) {
        log.info("[RESUME] Requested resume for scan {} (no background subscription; SSE will drive)", scanId);
        return ResponseEntity.accepted().build();
    }
}
