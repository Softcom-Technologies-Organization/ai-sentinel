package pro.softcom.aisentinel.application.pii.reporting.port.in;

import java.util.Optional;
import pro.softcom.aisentinel.domain.pii.reporting.PageSecretsResponse;

/**
 * In-port for revealing PII secrets from scan results.
 * Used by inbound adapters (e.g., REST controllers) to access decrypted PII data.
 * 
 * <p>Business Rule: Secrets can only be revealed when explicitly authorized
 * by configuration.</p>
 */
public interface RevealPiiSecretsPort {

    /**
     * Checks if secret revelation is currently allowed by configuration.
     *
     * @return true if revelation is allowed, false otherwise
     */
    boolean isRevealAllowed();

    /**
     * Reveals PII secrets for a specific page within a scan.
     * 
     * <p>This operation will automatically create an audit trail of the access.</p>
     *
     * @param scanId the scan identifier
     * @param pageId the Confluence page identifier
     * @return the page secrets if found and authorized, empty if not found
     * @throws SecurityException if revelation is not allowed by configuration
     */
    Optional<PageSecretsResponse> revealPageSecrets(String scanId, String pageId);
}
