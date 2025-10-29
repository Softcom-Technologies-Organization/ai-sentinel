package pro.softcom.sentinelle.infrastructure.pii.security.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JPA entity for PII access audit logs.
 * Tracks all access to decrypted PII data for GDPR/nLPD compliance.
 */
@Entity
@Table(name = "pii_access_audit")
@Data
@Builder
@NoArgsConstructor
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
            retentionUntil = accessedAt.plus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS); // 2 years
        }
    }
}
