package pro.softcom.aisentinel.domain.pii.detection;

/**
 * Snapshot of the on-demand concurrency benchmark job, combined with the
 * currently applied Ministral concurrency values.
 *
 * <p>The benchmark itself runs in the detector service, which reports its
 * lifecycle through the shared configuration row; this model only reads it.
 *
 * @param status         Job lifecycle: IDLE, PENDING, RUNNING, CANCEL_REQUESTED, CANCELLED, DONE or FAILED
 * @param progress       Job progress percentage (0..100)
 * @param message        Human-readable outcome or failure message (null when idle)
 * @param concurrency    Currently applied Ministral concurrency
 * @param tunedSignature The "host:port|model" signature the current concurrency was tuned for (null = never tuned)
 * @param maxConcurrency Highest concurrency level the benchmark measures (2..20)
 */
public record ConcurrencyBenchStatus(
    String status,
    int progress,
    String message,
    int concurrency,
    String tunedSignature,
    int maxConcurrency
) {
}
