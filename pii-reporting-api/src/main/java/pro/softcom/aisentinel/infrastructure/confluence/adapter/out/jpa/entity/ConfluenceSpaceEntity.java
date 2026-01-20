package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "confluence_spaces")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfluenceSpaceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "space_key", nullable = false, unique = true)
    private String spaceKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "description")
    private String description;

    @Column(name = "cache_timestamp", nullable = false)
    private LocalDateTime cacheTimestamp;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "confluence_last_modified_at")
    private LocalDateTime confluenceLastModifiedAt;
}
