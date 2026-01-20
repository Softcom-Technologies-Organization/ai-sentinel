package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the PII detector gRPC service.
 * Maps properties from application.yml for connecting to the Python PII detection microservice.
 */
@ConfigurationProperties(prefix = "pii-detector")
@Validated
public record PiiDetectorConfig(

    @NotBlank(message = "PII detector host cannot be blank")
    String host,

    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than 65536")
    int port,

    /*
     * Default confidence threshold for PII detection (0.0-1.0)
     */
    @Min(value = 0, message = "Threshold must be between 0.0 and 1.0")
    @Max(value = 1, message = "Threshold must be between 0.0 and 1.0")
    float defaultThreshold,

    @Min(value = 1000, message = "Connection timeout must be at least 1000ms")
    long connectionTimeoutMs,

    @Min(value = 1000, message = "Request timeout must be at least 1000ms")
    long requestTimeoutMs
) {
}
