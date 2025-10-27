package pro.softcom.sentinelle.infrastructure.pii.security.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.sentinelle.infrastructure.pii.security.jpa.entity.PiiAccessAuditEntity;

import java.time.Instant;

/**
 * Repository for PII access audit logs.
 */
public interface PiiAccessAuditRepository extends JpaRepository<PiiAccessAuditEntity, Long> {
    
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
