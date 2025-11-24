package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    //TODO: this field needs to be renamed for better clarity
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
}
