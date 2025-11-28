package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to scan_checkpoints table.
 * Business note: represents the fine-grained resume cursor per scan & space.
 */
@Getter
@Setter
@Entity
@Table(name = "scan_checkpoints")
@IdClass(ScanCheckpointId.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanCheckpointEntity {

    @Id
    @Column(name = "scan_id", nullable = false)
    private String scanId;

    @Version
    private Long version;

    @Id
    @Column(name = "space_key", nullable = false)
    private String spaceKey;

    @Column(name = "last_processed_page_id")
    private String lastProcessedPageId;

    @Column(name = "last_processed_attachment_name")
    private String lastProcessedAttachmentName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "progress_percentage")
    private Double progressPercentage;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
