package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for event-sourced scan events. Payload stores the serialized ScanEvent as JSONB.
 */
@Getter
@Setter
@Entity
@Table(name = "scan_events")
@IdClass(ScanEventId.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanEventEntity {

    @Id
    @Column(name = "scan_id", nullable = false)
    private String scanId;

    @Id
    @Column(name = "event_seq", nullable = false)
    private long eventSeq;

    @Column(name = "space_key")
    private String spaceKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "page_id")
    private String pageId;

    @Column(name = "page_title")
    private String pageTitle;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_type")
    private String attachmentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;
}
