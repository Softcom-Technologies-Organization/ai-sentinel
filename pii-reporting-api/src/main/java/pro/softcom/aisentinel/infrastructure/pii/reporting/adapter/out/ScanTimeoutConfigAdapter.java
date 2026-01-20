package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.infrastructure.config.ScanTimeoutConfig;

import java.time.Duration;

/**
 * Adapter providing scan timeout configuration from Spring properties.
 * 
 * Business purpose: Bridges the infrastructure timeout configuration to the application layer
 * via the port-out interface, maintaining hexagonal architecture boundaries.
 */
@Component
@RequiredArgsConstructor
public class ScanTimeoutConfigAdapter implements ScanTimeOutConfig {

    private final ScanTimeoutConfig springConfig;

    @Override
    public Duration getPiiDetection() {
        return springConfig.getPiiDetection();
    }
}
