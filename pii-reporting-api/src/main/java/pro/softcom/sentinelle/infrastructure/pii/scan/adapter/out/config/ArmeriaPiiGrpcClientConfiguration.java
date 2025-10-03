package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config;

import com.linecorp.armeria.client.grpc.GrpcClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pii_detection.PIIDetectionServiceGrpc;

/**
 * Provides an Armeria-based gRPC client stub for the PII Detection microservice.
 * This configuration is activated when property 'pii-detector.client=armeria'.
 *
 * Business intent: offer a more resilient HTTP/2/gRPC client with keepalive and
 * deadline support, while keeping the domain API unchanged.
 */
@Configuration
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "armeria")
public class ArmeriaPiiGrpcClientConfiguration {

    /**
     * Builds an Armeria gRPC blocking stub targeting the configured PII service.
     * Uses HTTP/2 cleartext (gproto+h2c) and normalizes localhost to 127.0.0.1 for Windows stability.
     * Applies response/connect timeouts from configuration to avoid default 15s deadline.
     */
    @Bean
    public PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub piiDetectionBlockingStub(PiiDetectorConfig config) {
        String host = normalizeHost(config.host());
        String uri = String.format("gproto+h2c://%s:%d", host, config.port());
        return GrpcClients.builder(uri)
                .responseTimeoutMillis(config.requestTimeoutMs())
                .build(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "127.0.0.1";
        String h = host.trim();
        return ("localhost".equalsIgnoreCase(h)) ? "127.0.0.1" : h;
    }
}
