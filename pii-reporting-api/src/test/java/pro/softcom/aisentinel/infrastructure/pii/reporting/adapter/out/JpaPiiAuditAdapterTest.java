package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.security.PiiAuditRecord;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.PiiAccessAuditRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.PiiAccessAuditEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaPiiAuditAdapter - persistence mapping and retention purge")
class JpaPiiAuditAdapterTest {

    @Mock
    private PiiAccessAuditRepository repository;

    @Captor
    private ArgumentCaptor<PiiAccessAuditEntity> entityCaptor;

    @InjectMocks
    private JpaPiiAuditAdapter adapter;

    @Test
    @DisplayName("Should_MapAndSaveEntity_When_SavingPiiAuditRecord")
    void Should_MapAndSaveEntity_When_SavingPiiAuditRecord() {
        // Given
        Instant accessedAt = Instant.parse("2024-01-01T10:15:30.00Z");
        Instant retentionUntil = Instant.parse("2026-01-01T10:15:30.00Z");

        PiiAuditRecord auditRecord = new PiiAuditRecord(
                "scan-123",
                "SPACE-KEY",
                "PAGE-ID",
                "A page title",
                accessedAt,
                retentionUntil,
                AccessPurpose.USER_DISPLAY,
                7
        );

        // When
        adapter.save(auditRecord);

        // Then
        verify(repository).save(entityCaptor.capture());
        PiiAccessAuditEntity savedEntity = entityCaptor.getValue();

        assertSoftly(softly -> {
            softly.assertThat(savedEntity.getScanId()).isEqualTo(auditRecord.scanId());
            softly.assertThat(savedEntity.getSpaceKey()).isEqualTo(auditRecord.spaceKey());
            softly.assertThat(savedEntity.getPageId()).isEqualTo(auditRecord.pageId());
            softly.assertThat(savedEntity.getPageTitle()).isEqualTo(auditRecord.pageTitle());
            softly.assertThat(savedEntity.getAccessedAt()).isEqualTo(auditRecord.accessedAt());
            softly.assertThat(savedEntity.getRetentionUntil()).isEqualTo(auditRecord.retentionUntil());
            softly.assertThat(savedEntity.getPurpose()).isEqualTo(auditRecord.purpose().name());
            softly.assertThat(savedEntity.getPiiEntitiesCount()).isEqualTo(auditRecord.piiEntitiesCount());
        });
    }

    @Test
    @DisplayName("Should_DelegateDeletionToRepository_When_DeletingExpiredRecords")
    void Should_DelegateDeletionToRepository_When_DeletingExpiredRecords() {
        // Given
        Instant expirationDate = Instant.parse("2023-12-31T23:59:59.00Z");
        when(repository.deleteByRetentionUntilBefore(any(Instant.class))).thenReturn(3);

        // When
        int deletedCount = adapter.deleteExpiredRecords(expirationDate);

        // Then
        verify(repository, times(1)).deleteByRetentionUntilBefore(expirationDate);
        assertThat(deletedCount).isEqualTo(3);
    }
}
