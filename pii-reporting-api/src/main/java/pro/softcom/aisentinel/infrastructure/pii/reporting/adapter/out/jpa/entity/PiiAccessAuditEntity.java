package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * JPA entity for PII access audit logs.
 * Tracks all access to decrypted PII data for GDPR/nLPD compliance.
 */
@Entity
@Table(name = "pii_access_audit")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class PiiAccessAuditEntity {
    private static final long DEFAULT_RETENTION_DAYS = 730;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "scan_id", nullable = false, length = 100)
    private String scanId;
    
    @Column(name = "page_id", length = 100)
    private String pageId;
    
    @Column(name = "page_title", length = 500)
    private String pageTitle;
    
    @Column(name = "space_key", length = 50)
    private String spaceKey;
    
    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
    
    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose;
    
    @Column(name = "pii_entities_count")
    private Integer piiEntitiesCount;
    
    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;
    
    /**
     * Calculates retention date (2 years from access by default).
     * Called automatically before persist.
     */
    @PrePersist
    void calculateRetention() {
        if (retentionUntil == null && accessedAt != null) {
            retentionUntil = accessedAt.plus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        Class<?> oEffectiveClass = o instanceof HibernateProxy hibernateProxy
            ? hibernateProxy.getHibernateLazyInitializer()
            .getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy hibernateProxy
            ? hibernateProxy.getHibernateLazyInitializer()
            .getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        PiiAccessAuditEntity that = (PiiAccessAuditEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hibernateProxy
            ? hibernateProxy.getHibernateLazyInitializer()
            .getPersistentClass().hashCode() : getClass().hashCode();
    }
}
