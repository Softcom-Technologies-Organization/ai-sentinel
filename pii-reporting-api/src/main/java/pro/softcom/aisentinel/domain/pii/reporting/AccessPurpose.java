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

    /**
     * Decryption of PII values by a redaction job in order to locate and replace them
     * in the source document. Each batch decryption is audit-logged.
     */
    REDACTION,

    /**
     * Decryption of PII values by an LLM judge job so the model can review each finding in its
     * context. Each space-level decryption is audit-logged.
     */
    LLM_JUDGE,

    // Future purposes can be added here as needed
}
