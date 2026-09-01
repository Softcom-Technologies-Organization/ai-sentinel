package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageConcurrencyBenchmarkPort;
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.ConcurrencyBenchStatusResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.RunConcurrencyBenchmarkRequestDto;

/**
 * REST API endpoint for the on-demand Ministral concurrency benchmark.
 *
 * <p>Business purpose: Allows administrators to trigger a concurrency
 * benchmark without restarting the detector service and to poll its progress.
 * The benchmark itself runs in the detector service; this API only flags the
 * request and reads back the job status.
 */
@RestController
@RequestMapping("/api/v1/pii-detection/concurrency-benchmark")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Concurrency Benchmark", description = "Trigger and monitor the Ministral concurrency benchmark")
public class ConcurrencyBenchmarkController {

    private static final int DEFAULT_MAX_CONCURRENCY = 4;

    private final ManageConcurrencyBenchmarkPort manageConcurrencyBenchmarkPort;

    /**
     * Requests an on-demand concurrency benchmark run.
     *
     * @param request optional body carrying the highest concurrency to measure (2..20)
     * @return 202 Accepted with the PENDING job status
     */
    @PostMapping("/run")
    @Operation(summary = "Request an on-demand concurrency benchmark run")
    public ResponseEntity<@NonNull ConcurrencyBenchStatusResponseDto> runBenchmark(
            @Valid @RequestBody(required = false) RunConcurrencyBenchmarkRequestDto request) {
        int maxConcurrency = request != null && request.maxConcurrency() != null
                ? request.maxConcurrency() : DEFAULT_MAX_CONCURRENCY;
        log.info("POST /api/v1/pii-detection/concurrency-benchmark/run - Requesting benchmark run up to concurrency {}",
                 maxConcurrency);

        manageConcurrencyBenchmarkPort.requestBenchmark(maxConcurrency);
        ConcurrencyBenchStatus status = manageConcurrencyBenchmarkPort.getBenchStatus();

        log.info("Concurrency benchmark run requested successfully");
        return ResponseEntity.accepted().body(toResponseDto(status));
    }

    /**
     * Stops the pending or running benchmark; the applied concurrency is left as is.
     *
     * @return 202 Accepted with the job status after the cancellation request
     */
    @PostMapping("/cancel")
    @Operation(summary = "Stop the pending or running concurrency benchmark")
    public ResponseEntity<@NonNull ConcurrencyBenchStatusResponseDto> cancelBenchmark() {
        log.info("POST /api/v1/pii-detection/concurrency-benchmark/cancel - Cancelling benchmark");
        manageConcurrencyBenchmarkPort.cancelBenchmark();
        ConcurrencyBenchStatus status = manageConcurrencyBenchmarkPort.getBenchStatus();
        return ResponseEntity.accepted().body(toResponseDto(status));
    }

    /**
     * Retrieves the current benchmark job status.
     *
     * @return Current job status with the applied concurrency values
     */
    @GetMapping("/status")
    @Operation(summary = "Get current concurrency benchmark status")
    public ResponseEntity<@NonNull ConcurrencyBenchStatusResponseDto> getStatus() {
        log.debug("GET /api/v1/pii-detection/concurrency-benchmark/status - Retrieving benchmark status");

        ConcurrencyBenchStatus status = manageConcurrencyBenchmarkPort.getBenchStatus();
        return ResponseEntity.ok(toResponseDto(status));
    }

    /**
     * Converts domain model to response DTO.
     */
    private ConcurrencyBenchStatusResponseDto toResponseDto(ConcurrencyBenchStatus status) {
        return new ConcurrencyBenchStatusResponseDto(
            status.status(),
            status.progress(),
            status.message(),
            status.concurrency(),
            status.tunedSignature(),
            status.maxConcurrency()
        );
    }
}
