package pro.softcom.aisentinel.application.pii.detection.port.out;

import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

/**
 * Port OUT for PII detection configuration persistence.
 * Defines repository operations for PII detection configuration.
 */
public interface PiiDetectionConfigRepository {

    /**
     * Retrieves the current PII detection configuration.
     * Since configuration is a singleton (single row), this always returns the config.
     *
     * @return The current PII detection configuration
     * @throws RuntimeException if configuration cannot be retrieved
     */
    PiiDetectionConfig findConfig();

    /**
     * Updates the PII detection configuration.
     * Since configuration is a singleton (single row), this updates the existing config.
     *
     * @param config The new configuration to persist
     * @throws IllegalArgumentException if config is invalid
     * @throws RuntimeException if update fails
     */
    void updateConfig(PiiDetectionConfig config);

    /**
     * Flags an on-demand concurrency benchmark request on the configuration row.
     * Resets the job status to PENDING with zero progress and no message.
     *
     * @param maxConcurrency Highest concurrency level the benchmark measures
     * @throws RuntimeException if the request cannot be persisted
     */
    void requestBenchmark(int maxConcurrency);

    /**
     * Cancels the benchmark job: a request not yet claimed by the detector
     * service is withdrawn (CANCELLED), a running one is asked to stop
     * (CANCEL_REQUESTED, the detector service confirms with CANCELLED).
     *
     * @throws RuntimeException if the cancellation cannot be persisted
     */
    void cancelBenchmark();

    /**
     * Retrieves the benchmark job status written by the detector service,
     * together with the currently applied concurrency values.
     *
     * @return The current benchmark status snapshot
     * @throws RuntimeException if the status cannot be retrieved
     */
    ConcurrencyBenchStatus findBenchStatus();
}
