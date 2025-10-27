package pro.softcom.sentinelle.application.pii.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.infrastructure.pii.security.jpa.PiiAccessAuditRepository;
import pro.softcom.sentinelle.infrastructure.pii.security.jpa.entity.PiiAccessAuditEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Audit service for PII access tracking (GDPR/nLPD compliance).
 * Logs all access to decrypted PII data with purpose and retention management.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PiiAccessAuditService {
    
    private final PiiAccessAuditRepository auditRepository;
    
    /**
     * Configurable retention period in days (default: 730 days = 2 years for nLPD compliance).
     */
    @Value("${pii.audit.retention-days:730}")
    private int retentionDays;
    
    /**
     * Audits PII access for compliance purposes.
     * 
     * @param scanId scan identifier
     * @param purpose access purpose (for audit trail)
     * @param piiCount number of PII entities accessed
     */
    public void auditPiiAccess(String scanId, 
                               AccessPurpose purpose, 
                               int piiCount) {
        try {
            Instant now = Instant.now();
            Instant retention = now.plus(retentionDays, ChronoUnit.DAYS);
            
            PiiAccessAuditEntity audit = PiiAccessAuditEntity.builder()
                .scanId(scanId)
                .accessedAt(now)
                .retentionUntil(retention)
                .purpose(purpose.name())
                .piiEntitiesCount(piiCount)
                .build();
                
            auditRepository.save(audit);
            
            log.info("[PII_ACCESS_AUDIT] scanId={} purpose={} count={} retentionUntil={}", 
                     scanId, purpose, piiCount, retention);
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
            int deleted = auditRepository.deleteByRetentionUntilBefore(now);
            log.info("[PII_AUDIT_PURGE] Deleted {} expired audit logs", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("[PII_AUDIT_PURGE] Failed to purge logs: {}", e.getMessage(), e);
            return 0;
        }
    }
}
