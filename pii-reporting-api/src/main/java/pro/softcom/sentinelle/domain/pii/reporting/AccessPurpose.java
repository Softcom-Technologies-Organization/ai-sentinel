package pro.softcom.sentinelle.domain.pii.reporting;

/**
 * Purpose for accessing decrypted PII data.
 * Used for audit trail and compliance (GDPR Art. 30, nLPD Art. 12).
 * 
 * New purposes can be added as business needs evolve.
 */
public enum AccessPurpose {
    /**
     * General administrative review of scan results.
     * Covers dashboard viewing, report generation without export.
     */
    ADMIN_REVIEW,
    
    /**
     * Data analysis for compliance reporting.
     * Includes metrics, statistics, trend analysis.
     */
    COMPLIANCE_REPORTING,
    
    /**
     * Technical investigation or support.
     * Debugging issues, customer support cases.
     */
    TECHNICAL_SUPPORT,
    
    /**
     * Data export for external use.
     * Includes downloads, API exports, file generation.
     */
    DATA_EXPORT

    // Future purposes can be added here as needed
}
