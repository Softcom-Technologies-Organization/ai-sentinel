package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageConcurrencyBenchmarkPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;

/**
 * Use case for the on-demand Ministral concurrency benchmark.
 * Handles benchmark run requests and status polling; the benchmark itself is
 * executed by the detector service, which reports progress through the
 * configuration row.
 */
public class ManageConcurrencyBenchmarkUseCase implements ManageConcurrencyBenchmarkPort {

    private static final Logger log = LoggerFactory.getLogger(ManageConcurrencyBenchmarkUseCase.class);

    public static final int MIN_BENCH_CONCURRENCY = 2;
    public static final int MAX_BENCH_CONCURRENCY = 20;

    private final PiiDetectionConfigRepository repository;

    public ManageConcurrencyBenchmarkUseCase(PiiDetectionConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void requestBenchmark(int maxConcurrency) {
        if (maxConcurrency < MIN_BENCH_CONCURRENCY || maxConcurrency > MAX_BENCH_CONCURRENCY) {
            throw new IllegalArgumentException(
                "maxConcurrency must be between " + MIN_BENCH_CONCURRENCY + " and "
                    + MAX_BENCH_CONCURRENCY + ", got " + maxConcurrency);
        }
        ConcurrencyBenchStatus current = repository.findBenchStatus();
        if (isInProgress(current)) {
            log.info(
                "Concurrency benchmark already {} — ignoring duplicate request",
                current.status()
            );
            return;
        }
        log.info("Requesting on-demand Ministral concurrency benchmark run up to concurrency {}", maxConcurrency);
        repository.requestBenchmark(maxConcurrency);
    }

    @Override
    public void cancelBenchmark() {
        ConcurrencyBenchStatus current = repository.findBenchStatus();
        if (!isInProgress(current)) {
            log.info("No concurrency benchmark in progress — nothing to cancel");
            return;
        }
        log.info("Cancelling concurrency benchmark ({})", current.status());
        repository.cancelBenchmark();
    }

    /**
     * A benchmark is in progress once it has been requested (PENDING), is
     * actively RUNNING, or is winding down after a cancellation. Re-arming the
     * request flag while in progress would make the detector service run a
     * redundant second benchmark right after the current one, so duplicate
     * requests are ignored.
     */
    private static boolean isInProgress(ConcurrencyBenchStatus status) {
        if (status == null) {
            return false;
        }
        return "PENDING".equals(status.status())
            || "RUNNING".equals(status.status())
            || "CANCEL_REQUESTED".equals(status.status());
    }

    @Override
    public ConcurrencyBenchStatus getBenchStatus() {
        log.debug("Retrieving concurrency benchmark status");
        return repository.findBenchStatus();
    }
}
