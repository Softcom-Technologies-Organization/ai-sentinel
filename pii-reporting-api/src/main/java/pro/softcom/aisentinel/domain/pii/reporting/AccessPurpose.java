package pro.softcom.aisentinel.domain.pii.reporting;

/**
 * Purpose for accessing decrypted PII data.
 * Used for audit trail and compliance (GDPR Art. 30, nLPD Art. 12).
 * <p>
 * New purposes can be added as business needs evolve.
 */
public enum AccessPurpose {
    /**
     * Display of PII data to authorized users through the UI.
     * Used when users request to reveal sensitive information via the API.
     * Triggers decryption for presentation purposes with full audit logging.
     */
    USER_DISPLAY,

    // Future purposes can be added here as needed
}
