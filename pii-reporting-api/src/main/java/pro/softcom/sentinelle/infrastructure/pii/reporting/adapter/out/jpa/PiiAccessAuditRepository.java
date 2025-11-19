package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa;

import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.PiiAccessAuditEntity;

/**
 * Repository for PII access audit logs.
 */
public interface PiiAccessAuditRepository extends JpaRepository<@NonNull PiiAccessAuditEntity, @NonNull Long> {
    
    /**
     * Deletes all audit logs expired before the given instant.
     * Used for automatic retention policy enforcement (nLPD Art. 6).
     * 
     * @param instant retention deadline
     * @return number of deleted records
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PiiAccessAuditEntity WHERE retentionUntil < :instant")
    int deleteByRetentionUntilBefore(Instant instant);
}
