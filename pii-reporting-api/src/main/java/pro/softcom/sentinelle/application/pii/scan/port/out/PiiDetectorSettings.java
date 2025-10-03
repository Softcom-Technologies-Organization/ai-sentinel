package pro.softcom.sentinelle.application.pii.scan.port.out;

/**
 * Application-level settings required to contact the PII detector.
 * This prevents leaking infrastructure configuration objects into the application layer.
 */
public interface PiiDetectorSettings {
    String host();
    int port();
    float defaultThreshold();
}
