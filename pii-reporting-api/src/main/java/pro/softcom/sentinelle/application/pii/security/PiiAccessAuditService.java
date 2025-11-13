package pro.softcom.sentinelle.application.pii.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.domain.pii.security.PiiAuditRecord;

/**
 * Audit service for PII access tracking (GDPR/nLPD compliance).
 * Logs all access to decrypted PII data with purpose and retention management.
 *
 * <p><b>Current Granularity: SCAN level</b></p>
 *
 * <p>The current implementation audits access at the <b>SCAN</b> level, meaning one audit
 * record is created per scan access, regardless of the number of PII entities accessed.</p>
 *
 * @see PiiAuditRecord
 * @see AccessPurpose
 */
@Slf4j
public class PiiAccessAuditService {

    private final SavePiiAuditPort savePiiAuditPort;

    /**
     * Configurable retention period in days (default: 730 days = 2 years for nLPD compliance).
     */
    private final int retentionDays;

    public PiiAccessAuditService(SavePiiAuditPort savePiiAuditPort, int retentionDays) {
        this.savePiiAuditPort = savePiiAuditPort;
        this.retentionDays = retentionDays;
    }

    /**
     * Audits PII access for compliance purposes.
     *
     * <p><b>Current Granularity: SCAN level</b></p>
     *
     * <p>This method creates <b>one audit record per scan access</b>. All PII entities
     * in the scan are counted as a single access event.</p>
     *
     * <p><b>Limitation:</b> Cannot track which specific PII entities or pages were accessed.</p>
     *
     * @param scanId   scan identifier (current granularity level)
     * @param purpose  access purpose (for audit trail)
     * @param piiCount total number of PII entities in the scan
     */
    public void auditPiiAccess(String scanId, AccessPurpose purpose, int piiCount) {
        auditPiiAccess(scanId, null, null, null, purpose, piiCount);
    }
    
    /**
     * Audits PII access for compliance purposes at PAGE level.
     *
     * <p><b>Granularity: PAGE level</b></p>
     *
     * <p>This method creates <b>one audit record per page access</b>, tracking which
     * specific Confluence page had its PII revealed.</p>
     *
     * @param scanId    scan identifier
     * @param pageId    Confluence page identifier
     * @param pageTitle Confluence page title (for human readability)
     * @param spaceKey  Confluence space key
     * @param purpose   access purpose (for audit trail)
     * @param piiCount  number of PII entities revealed on this page
     */
    public void auditPiiAccess(String scanId, String spaceKey, String pageId, String pageTitle,
                               AccessPurpose purpose, int piiCount) {
        try {
            Instant now = Instant.now();
            Instant retention = now.plus(retentionDays, ChronoUnit.DAYS);

            PiiAuditRecord audit = new PiiAuditRecord(
                    scanId,
                    spaceKey,
                    pageId,
                    pageTitle,
                    now,
                    retention,
                    purpose,
                    piiCount
            );

            savePiiAuditPort.save(audit);

            log.info("[PII_ACCESS_AUDIT] scanId={} pageId={} spaceKey={} purpose={} count={} retentionUntil={}",
                    scanId, pageId, spaceKey, purpose, piiCount, retention);
        } catch (Exception e) {
            // Never fail the main flow due to audit failure
            log.error("[PII_ACCESS_AUDIT] Failed to log audit: {}", e.getMessage(), e);
        }
    }

    /**
     * Purges expired audit logs (called by scheduled job).
     * Enforces nLPD Art. 6 data minimization principle.
     *
     * @return number of deleted records
     */
    public int purgeExpiredLogs() {
        try {
            Instant now = Instant.now();
            int deleted = savePiiAuditPort.deleteExpiredRecords(now);
            log.info("[PII_AUDIT_PURGE] Deleted {} expired audit logs", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("[PII_AUDIT_PURGE] Failed to purge logs: {}", e.getMessage(), e);
            return 0;
        }
    }
}
