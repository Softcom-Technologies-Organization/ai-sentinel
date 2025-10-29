package pro.softcom.sentinelle.application.pii.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to purge expired PII access audit logs.
 * Ensures compliance with nLPD data minimization (Art. 6).
 * <p>
 * Can be disabled via property: pii.audit.retention.enabled=false
 */
@Component
@ConditionalOnProperty(name = "pii.audit.retention.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PiiAuditRetentionJob {

    private final PiiAccessAuditService auditService;

    /**
     * Runs daily at 3 AM (cron: second minute hour day month weekday).
     * Schedule is configurable via pii.audit.purge-cron property.
     */
    @Scheduled(cron = "${pii.audit.purge-cron:0 0 3 * * ?}")
    public void purgeExpiredAuditLogs() {
        log.info("[PII_AUDIT_PURGE] Starting scheduled purge of expired audit logs");
        try {
            int deleted = auditService.purgeExpiredLogs();
            log.info("[PII_AUDIT_PURGE] Completed: {} records deleted", deleted);
        } catch (Exception e) {
            log.error("[PII_AUDIT_PURGE] Failed: {}", e.getMessage(), e);
        }
    }
}
