package pro.softcom.aisentinel.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration for scan operation timeouts.
 * Business intent: Externalizes timeout values to allow environment-specific tuning
 * of reactive operation timeouts during PII detection scans.
 */
@Configuration
@ConfigurationProperties(prefix = "scan.timeouts")
@Validated
@Getter
@Setter
public class ScanTimeoutConfig {

    /**
     * Timeout for PII detection operations on page content and attachments.
     * Should be slightly higher than the gRPC timeout (300s) to allow gRPC exceptions
     * to trigger first and provide more specific error information.
     * Default: 305 seconds
     */
    private Duration piiDetection = Duration.ofSeconds(305);
}
