package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ReadPiiConfigPort;
import pro.softcom.sentinelle.infrastructure.config.properties.PiiReportingProperties;

/**
 * Adapter for reading PII reporting configuration.
 * Implements the out-port for hexagonal architecture compliance.
 */
@Component
@RequiredArgsConstructor
public class PiiConfigAdapter implements ReadPiiConfigPort {

    private final PiiReportingProperties properties;

    @Override
    public boolean isAllowSecretReveal() {
        return properties.isAllowSecretReveal();
    }
}
