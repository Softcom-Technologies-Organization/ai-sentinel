package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

/**
 * DTO representing the on-demand concurrency benchmark job status for REST
 * API responses.
 *
 * <p>Business purpose: Lets clients poll the progress of a benchmark run
 * executed by the detector service and display the currently applied
 * concurrency values.
 *
 * @param status         Job lifecycle: IDLE, PENDING, RUNNING, CANCEL_REQUESTED, CANCELLED, DONE or FAILED
 * @param progress       Job progress percentage (0..100)
 * @param message        Human-readable outcome or failure message (null when idle)
 * @param concurrency    Currently applied Ministral concurrency
 * @param tunedSignature The "host:port|model" signature the current concurrency was tuned for (null = never tuned)
 * @param maxConcurrency Highest concurrency level the benchmark measures (2..20)
 */
public record ConcurrencyBenchStatusResponseDto(
    String status,
    int progress,
    String message,
    int concurrency,
    String tunedSignature,
    int maxConcurrency
) {
}
