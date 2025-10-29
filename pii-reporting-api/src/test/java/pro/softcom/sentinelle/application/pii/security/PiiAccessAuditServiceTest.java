package pro.softcom.sentinelle.application.pii.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.infrastructure.pii.security.jpa.PiiAccessAuditRepository;
import pro.softcom.sentinelle.infrastructure.pii.security.jpa.entity.PiiAccessAuditEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiAccessAuditService - PII access audit tracking")
class PiiAccessAuditServiceTest {

    @Mock
    private PiiAccessAuditRepository auditRepository;

    @Captor
    private ArgumentCaptor<PiiAccessAuditEntity> auditEntityCaptor;

    private PiiAccessAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new PiiAccessAuditService(auditRepository);
        // Set default retention to 730 days
        ReflectionTestUtils.setField(auditService, "retentionDays", 730);
    }

    // ========== auditPiiAccess Tests ==========

    @Test
    @DisplayName("Should_SaveAuditLog_When_AuditingPiiAccess")
    void Should_SaveAuditLog_When_AuditingPiiAccess() {
        // Given
        String scanId = "scan-123";
        AccessPurpose purpose = AccessPurpose.USER_DISPLAY;
        int piiCount = 5;

        // When
        auditService.auditPiiAccess(scanId, purpose, piiCount);

        // Then
        verify(auditRepository).save(auditEntityCaptor.capture());
        PiiAccessAuditEntity savedEntity = auditEntityCaptor.getValue();
        
        assertSoftly(softly -> {
            softly.assertThat(savedEntity.getScanId()).isEqualTo(scanId);
            softly.assertThat(savedEntity.getPurpose()).isEqualTo(purpose.name());
            softly.assertThat(savedEntity.getPiiEntitiesCount()).isEqualTo(piiCount);
            softly.assertThat(savedEntity.getAccessedAt()).isNotNull();
            softly.assertThat(savedEntity.getRetentionUntil()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should_SetRetentionDate_When_AuditingPiiAccess")
    void Should_SetRetentionDate_When_AuditingPiiAccess() {
        // Given
        String scanId = "scan-123";
        Instant before = Instant.now().plus(729, ChronoUnit.DAYS);

        // When
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 3);

        // Then
        verify(auditRepository).save(auditEntityCaptor.capture());
        PiiAccessAuditEntity savedEntity = auditEntityCaptor.getValue();
        Instant after = Instant.now().plus(731, ChronoUnit.DAYS);

        assertThat(savedEntity.getRetentionUntil())
            .isBetween(before, after);
    }

    @Test
    @DisplayName("Should_AuditAllAccessPurposes_When_CalledWithDifferentPurposes")
    void Should_AuditAllAccessPurposes_When_CalledWithDifferentPurposes() {
        // Given
        String scanId = "scan-123";

        // When/Then
        for (AccessPurpose purpose : AccessPurpose.values()) {
            auditService.auditPiiAccess(scanId, purpose, 1);
        }

        verify(auditRepository, times(AccessPurpose.values().length)).save(any());
    }

    @Test
    @DisplayName("Should_HandleZeroPiiCount_When_AuditingAccess")
    void Should_HandleZeroPiiCount_When_AuditingAccess() {
        // Given
        String scanId = "scan-123";

        // When
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 0);

        // Then
        verify(auditRepository).save(auditEntityCaptor.capture());
        assertThat(auditEntityCaptor.getValue().getPiiEntitiesCount()).isZero();
    }

    @Test
    @DisplayName("Should_NotThrowException_When_RepositorySaveFails")
    void Should_NotThrowException_When_RepositorySaveFails() {
        // Given
        when(auditRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        // When/Then - Should not throw
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, 2);

        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("Should_UseCustomRetention_When_ConfiguredDifferently")
    void Should_UseCustomRetention_When_ConfiguredDifferently() {
        // Given
        ReflectionTestUtils.setField(auditService, "retentionDays", 365);
        Instant before = Instant.now().plus(364, ChronoUnit.DAYS);

        // When
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, 1);

        // Then
        verify(auditRepository).save(auditEntityCaptor.capture());
        Instant after = Instant.now().plus(366, ChronoUnit.DAYS);

        assertThat(auditEntityCaptor.getValue().getRetentionUntil())
            .isBetween(before, after);
    }

    // ========== purgeExpiredLogs Tests ==========

    @Test
    @DisplayName("Should_DeleteExpiredLogs_When_PurgingExpiredLogs")
    void Should_DeleteExpiredLogs_When_PurgingExpiredLogs() {
        // Given
        int expectedDeleted = 10;
        when(auditRepository.deleteByRetentionUntilBefore(any(Instant.class)))
            .thenReturn(expectedDeleted);

        // When
        int actualDeleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(actualDeleted).isEqualTo(expectedDeleted);
        verify(auditRepository).deleteByRetentionUntilBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should_DeleteWithCurrentTime_When_PurgingExpiredLogs")
    void Should_DeleteWithCurrentTime_When_PurgingExpiredLogs() {
        // Given
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        Instant before = Instant.now();

        // When
        auditService.purgeExpiredLogs();

        // Then
        verify(auditRepository).deleteByRetentionUntilBefore(instantCaptor.capture());
        Instant after = Instant.now();

        assertThat(instantCaptor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should_ReturnZero_When_NoLogsToDelete")
    void Should_ReturnZero_When_NoLogsToDelete() {
        // Given
        when(auditRepository.deleteByRetentionUntilBefore(any(Instant.class)))
            .thenReturn(0);

        // When
        int deleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("Should_ReturnZeroAndNotThrow_When_PurgeFailsWithException")
    void Should_ReturnZeroAndNotThrow_When_PurgeFailsWithException() {
        // Given
        when(auditRepository.deleteByRetentionUntilBefore(any(Instant.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When
        int deleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(deleted).isZero();
        verify(auditRepository).deleteByRetentionUntilBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should_DeleteLargeNumberOfLogs_When_ManyLogsExpired")
    void Should_DeleteLargeNumberOfLogs_When_ManyLogsExpired() {
        // Given
        int largeNumber = 1000;
        when(auditRepository.deleteByRetentionUntilBefore(any(Instant.class)))
            .thenReturn(largeNumber);

        // When
        int deleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(deleted).isEqualTo(largeNumber);
    }

    // ========== Integration/Edge Cases ==========

    @Test
    @DisplayName("Should_AuditMultipleTimes_When_CalledSequentially")
    void Should_AuditMultipleTimes_When_CalledSequentially() {
        // Given
        String scanId = "scan-123";

        // When
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 1);
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 2);
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 3);

        // Then
        verify(auditRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("Should_HandleVeryLargePiiCount_When_AuditingAccess")
    void Should_HandleVeryLargePiiCount_When_AuditingAccess() {
        // Given
        int largePiiCount = Integer.MAX_VALUE;

        // When
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, largePiiCount);

        // Then
        verify(auditRepository).save(auditEntityCaptor.capture());
        assertThat(auditEntityCaptor.getValue().getPiiEntitiesCount())
            .isEqualTo(largePiiCount);
    }
}
