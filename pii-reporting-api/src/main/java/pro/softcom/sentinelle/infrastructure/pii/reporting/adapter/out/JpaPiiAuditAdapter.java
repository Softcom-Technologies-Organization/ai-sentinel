package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.sentinelle.domain.pii.security.PiiAuditRecord;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.PiiAccessAuditRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.PiiAccessAuditEntity;

/**
 * JPA adapter implementing the SavePiiAuditPort for hexagonal architecture compliance.
 * Bridges the gap between domain model (PiiAuditRecord) and persistence model (PiiAccessAuditEntity).
 */
@Component
@RequiredArgsConstructor
public class JpaPiiAuditAdapter implements SavePiiAuditPort {

    private final PiiAccessAuditRepository repository;

    @Override
    public void save(PiiAuditRecord auditRecord) {
        PiiAccessAuditEntity entity = PiiAccessAuditEntity.builder()
                .scanId(auditRecord.scanId())
                .spaceKey(auditRecord.spaceKey())
                .pageId(auditRecord.pageId())
                .pageTitle(auditRecord.pageTitle())
                .accessedAt(auditRecord.accessedAt())
                .retentionUntil(auditRecord.retentionUntil())
                .purpose(auditRecord.purpose().name())
                .piiEntitiesCount(auditRecord.piiEntitiesCount())
                .build();

        repository.save(entity);
    }

    @Override
    public int deleteExpiredRecords(Instant expirationDate) {
        return repository.deleteByRetentionUntilBefore(expirationDate);
    }
}
