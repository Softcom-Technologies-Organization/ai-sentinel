package pro.softcom.sentinelle.application.pii.reporting.port.out;

/**
 * Out-port for reading PII reporting configuration.
 * Abstracts the configuration source from the application layer.
 */
public interface ReadPiiConfigPort {

    /**
     * Checks if secret revelation is allowed by configuration.
     *
     * @return true if revelation is allowed, false otherwise
     */
    boolean isAllowSecretReveal();
}
