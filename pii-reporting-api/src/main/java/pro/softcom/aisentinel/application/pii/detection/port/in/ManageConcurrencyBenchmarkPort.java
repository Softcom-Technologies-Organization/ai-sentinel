package pro.softcom.aisentinel.application.pii.detection.port.in;

import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;

/**
 * Port IN for the on-demand Ministral concurrency benchmark.
 * Defines use cases for requesting a benchmark run and polling its status.
 */
public interface ManageConcurrencyBenchmarkPort {

    /**
     * Requests an on-demand concurrency benchmark run.
     * The detector service picks up the request and executes the benchmark.
     *
     * @param maxConcurrency Highest concurrency level to measure (2..20)
     * @throws IllegalArgumentException when maxConcurrency is out of range
     */
    void requestBenchmark(int maxConcurrency);

    /**
     * Stops the pending or running benchmark. The applied concurrency is left
     * untouched. A no-op when no benchmark is in progress.
     */
    void cancelBenchmark();

    /**
     * Retrieves the current benchmark job status together with the currently
     * applied concurrency values.
     *
     * @return The current benchmark status snapshot
     */
    ConcurrencyBenchStatus getBenchStatus();
}
