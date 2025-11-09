package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTimeOutConfig;

/**
 * Adapter providing scan timeout configuration from Spring properties.
 * 
 * Business purpose: Bridges the infrastructure timeout configuration to the application layer
 * via the port-out interface, maintaining hexagonal architecture boundaries.
 */
@Component
@RequiredArgsConstructor
public class ScanTimeoutConfigAdapter implements ScanTimeOutConfig {

    private final pro.softcom.sentinelle.infrastructure.config.ScanTimeoutConfig springConfig;

    @Override
    public Duration getPiiDetection() {
        return springConfig.getPiiDetection();
    }
}
