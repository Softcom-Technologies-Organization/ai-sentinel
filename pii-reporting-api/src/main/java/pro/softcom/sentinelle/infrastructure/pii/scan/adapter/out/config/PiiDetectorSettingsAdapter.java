package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config;

import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorSettings;

/**
 * Infrastructure adapter that exposes PiiDetectorConfig as the application-level PiiDetectorSettings.
 */
@Component
public class PiiDetectorSettingsAdapter implements PiiDetectorSettings {

    private final PiiDetectorConfig piiDetectorConfig;

    public PiiDetectorSettingsAdapter(PiiDetectorConfig piiDetectorConfig) {
        this.piiDetectorConfig = piiDetectorConfig;
    }

    @Override
    public String host() {
        return piiDetectorConfig.host();
    }

    @Override
    public int port() {
        return piiDetectorConfig.port();
    }

    @Override
    public float defaultThreshold() {
        return piiDetectorConfig.defaultThreshold();
    }
}
