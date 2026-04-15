package pro.softcom.aisentinel.application.pii.reporting.port.out;

import java.time.Duration;

/**
 * Output port exposing tunable timeouts used by the scan pipeline.
 *
 * <p>Abstracts the underlying configuration source (application properties, remote config, etc.)
 * so the application layer remains framework-agnostic.
 */
public interface ScanTimeOutConfig {

    /** Maximum duration allowed for a single PII detection call on a piece of content. */
    Duration getPiiDetection();
}
