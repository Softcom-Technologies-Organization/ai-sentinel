package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanPort;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
public class ResumeScanController {

    private final PauseScanPort pauseScanPort;

    @PostMapping("/{scanId}/resume")
    public ResponseEntity<@NonNull Void> resume(@PathVariable String scanId) {
        log.info("[RESUME] Requested resume for scan {} (no background subscription; SSE will drive)", scanId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{scanId}/pause")
    public ResponseEntity<@NonNull Void> pause(@PathVariable String scanId) {
        log.info("[PAUSE] Requested pause for scan {}", scanId);
        pauseScanPort.pauseScan(scanId);
        return ResponseEntity.accepted().build();
    }
}
