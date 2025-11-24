package pro.softcom.aisentinel.application.pii.security.port.out;

import java.time.Instant;
import pro.softcom.aisentinel.domain.pii.security.PiiAuditRecord;

/**
 * Out-port for persisting PII access audit records.
 * Ensures hexagonal architecture compliance by abstracting persistence details.
 */
public interface SavePiiAuditPort {

    /**
     * Saves a PII access audit record for compliance tracking.
     *
     * @param auditRecord the audit record to save
     */
    void save(PiiAuditRecord auditRecord);

    /**
     * Deletes all audit records expired before the given instant.
     * Used for automatic retention policy enforcement (nLPD Art. 6).
     *
     * @param expirationDate retention deadline
     * @return number of deleted records
     */
    int deleteExpiredRecords(Instant expirationDate);
}
