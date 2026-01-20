package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PiiAccessAuditEntityTest {

    @Test
    @DisplayName("should set default retentionUntil when it is null and accessedAt is provided")
    void Should_setDefaultRetentionUntil_When_RetentionIsNullAndAccessedAtProvided() {
        Instant accessedAt = Instant.parse("2024-01-01T10:15:30.00Z");
        PiiAccessAuditEntity entity = PiiAccessAuditEntity.builder()
            .scanId("scan-1")
            .purpose("REPORTING")
            .accessedAt(accessedAt)
            .build();

        entity.calculateRetention();

        assertThat(entity.getRetentionUntil())
            .isEqualTo(accessedAt.plusSeconds(730L * 24 * 60 * 60));
    }

    @Test
    @DisplayName("should not override retentionUntil when it is already set")
    void Should_notOverrideRetentionUntil_When_RetentionAlreadySet() {
        Instant accessedAt = Instant.parse("2024-01-01T10:15:30.00Z");
        Instant existingRetention = accessedAt.plusSeconds(10);
        PiiAccessAuditEntity entity = PiiAccessAuditEntity.builder()
            .scanId("scan-1")
            .purpose("REPORTING")
            .accessedAt(accessedAt)
            .retentionUntil(existingRetention)
            .build();

        entity.calculateRetention();

        assertThat(entity.getRetentionUntil()).isEqualTo(existingRetention);
    }

    @Test
    @DisplayName("should not set retentionUntil when accessedAt is null")
    void Should_notSetRetentionUntil_When_AccessedAtIsNull() {
        PiiAccessAuditEntity entity = PiiAccessAuditEntity.builder()
            .scanId("scan-1")
            .purpose("REPORTING")
            .build();

        entity.calculateRetention();

        assertThat(entity.getRetentionUntil()).isNull();
    }

    @Test
    @DisplayName("should consider entities equal when they have the same non-null id")
    void Should_ConsiderEntitiesEqual_When_SameNonNullId() {
        PiiAccessAuditEntity first = new PiiAccessAuditEntity();
        first.setId(1L);

        PiiAccessAuditEntity second = new PiiAccessAuditEntity();
        second.setId(1L);

        assertThat(first)
            .isEqualTo(second)
            .hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("should consider entities not equal when they have different ids")
    void Should_ConsiderEntitiesNotEqual_When_DifferentIds() {
        PiiAccessAuditEntity first = new PiiAccessAuditEntity();
        first.setId(1L);

        PiiAccessAuditEntity second = new PiiAccessAuditEntity();
        second.setId(2L);

        assertThat(first).isNotEqualTo(second);
    }
}
