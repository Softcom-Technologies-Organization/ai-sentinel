package pro.softcom.sentinelle.domain.pii.reporting;

/**
 * Purpose for accessing decrypted PII data.
 * Used for audit trail and compliance (GDPR Art. 30, nLPD Art. 12).
 * <p>
 * New purposes can be added as business needs evolve.
 */
public enum AccessPurpose {
    /**
     * General administrative review of scan results.
     * Covers dashboard viewing, report generation without export.
     */
    ADMIN_REVIEW,

    // Future purposes can be added here as needed
}
