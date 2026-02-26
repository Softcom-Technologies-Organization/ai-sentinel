package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity for Confluence connection configuration.
 * Represents the database table for storing Confluence connection settings.
 * Single-row configuration table (id always = 1).
 */
@Setter
@Getter
@Entity
@Table(name = "confluence_connection_config")
@Builder
@AllArgsConstructor
public class ConfluenceConnectionConfigEntity {

    @Id
    @Column(nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @NotNull
    @Column(name = "username", nullable = false)
    private String username;

    @NotNull
    @Column(name = "api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiTokenEncrypted;

    @NotNull
    @Min(value = 1, message = "Connect timeout must be positive")
    @Column(name = "connect_timeout", nullable = false)
    private Integer connectTimeout;

    @NotNull
    @Min(value = 1, message = "Read timeout must be positive")
    @Column(name = "read_timeout", nullable = false)
    private Integer readTimeout;

    @NotNull
    @Min(value = 0, message = "Max retries cannot be negative")
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @NotNull
    @Min(value = 1, message = "Pages limit must be positive")
    @Column(name = "pages_limit", nullable = false)
    private Integer pagesLimit;

    @NotNull
    @Min(value = 1, message = "Max pages must be positive")
    @Column(name = "max_pages", nullable = false)
    private Integer maxPages;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    protected ConfluenceConnectionConfigEntity() {
        // Required by JPA
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfluenceConnectionConfigEntity that = (ConfluenceConnectionConfigEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
