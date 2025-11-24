package pro.softcom.aisentinel.application.pii.security;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.security.PiiAuditRecord;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiAccessAuditService - PII access audit tracking")
class PiiAccessAuditServiceTest {

    @Mock
    private SavePiiAuditPort savePiiAuditPort;

    @Captor
    private ArgumentCaptor<PiiAuditRecord> auditRecordCaptor;

    private PiiAccessAuditService auditService;
    private static final int DEFAULT_RETENTION_DAYS = 730;

    @BeforeEach
    void setUp() {
        auditService = new PiiAccessAuditService(savePiiAuditPort, DEFAULT_RETENTION_DAYS);
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
        verify(savePiiAuditPort).save(auditRecordCaptor.capture());
        PiiAuditRecord savedRecord = auditRecordCaptor.getValue();
        
        assertSoftly(softly -> {
            softly.assertThat(savedRecord.scanId()).isEqualTo(scanId);
            softly.assertThat(savedRecord.purpose()).isEqualTo(purpose);
            softly.assertThat(savedRecord.piiEntitiesCount()).isEqualTo(piiCount);
            softly.assertThat(savedRecord.accessedAt()).isNotNull();
            softly.assertThat(savedRecord.retentionUntil()).isNotNull();
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
        verify(savePiiAuditPort).save(auditRecordCaptor.capture());
        PiiAuditRecord savedRecord = auditRecordCaptor.getValue();
        Instant after = Instant.now().plus(731, ChronoUnit.DAYS);

        assertThat(savedRecord.retentionUntil())
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

        verify(savePiiAuditPort, times(AccessPurpose.values().length)).save(any());
    }

    @Test
    @DisplayName("Should_HandleZeroPiiCount_When_AuditingAccess")
    void Should_HandleZeroPiiCount_When_AuditingAccess() {
        // Given
        String scanId = "scan-123";

        // When
        auditService.auditPiiAccess(scanId, AccessPurpose.USER_DISPLAY, 0);

        // Then
        verify(savePiiAuditPort).save(auditRecordCaptor.capture());
        assertThat(auditRecordCaptor.getValue().piiEntitiesCount()).isZero();
    }

    @Test
    @DisplayName("Should_NotThrowException_When_RepositorySaveFails")
    void Should_NotThrowException_When_RepositorySaveFails() {
        // Given
        doThrow(new RuntimeException("Database error")).when(savePiiAuditPort).save(any());

        // When/Then - Should not throw
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, 2);

        verify(savePiiAuditPort).save(any());
    }

    @Test
    @DisplayName("Should_UseCustomRetention_When_ConfiguredDifferently")
    void Should_UseCustomRetention_When_ConfiguredDifferently() {
        // Given
        int customRetentionDays = 365;
        auditService = new PiiAccessAuditService(savePiiAuditPort, customRetentionDays);
        Instant before = Instant.now().plus(364, ChronoUnit.DAYS);

        // When
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, 1);

        // Then
        verify(savePiiAuditPort).save(auditRecordCaptor.capture());
        Instant after = Instant.now().plus(366, ChronoUnit.DAYS);

        assertThat(auditRecordCaptor.getValue().retentionUntil())
            .isBetween(before, after);
    }

    // ========== purgeExpiredLogs Tests ==========

    @Test
    @DisplayName("Should_DeleteExpiredLogs_When_PurgingExpiredLogs")
    void Should_DeleteExpiredLogs_When_PurgingExpiredLogs() {
        // Given
        int expectedDeleted = 10;
        when(savePiiAuditPort.deleteExpiredRecords(any(Instant.class)))
            .thenReturn(expectedDeleted);

        // When
        int actualDeleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(actualDeleted).isEqualTo(expectedDeleted);
        verify(savePiiAuditPort).deleteExpiredRecords(any(Instant.class));
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
        verify(savePiiAuditPort).deleteExpiredRecords(instantCaptor.capture());
        Instant after = Instant.now();

        assertThat(instantCaptor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should_ReturnZero_When_NoLogsToDelete")
    void Should_ReturnZero_When_NoLogsToDelete() {
        // Given
        when(savePiiAuditPort.deleteExpiredRecords(any(Instant.class)))
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
        when(savePiiAuditPort.deleteExpiredRecords(any(Instant.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When
        int deleted = auditService.purgeExpiredLogs();

        // Then
        assertThat(deleted).isZero();
        verify(savePiiAuditPort).deleteExpiredRecords(any(Instant.class));
    }

    @Test
    @DisplayName("Should_DeleteLargeNumberOfLogs_When_ManyLogsExpired")
    void Should_DeleteLargeNumberOfLogs_When_ManyLogsExpired() {
        // Given
        int largeNumber = 1000;
        when(savePiiAuditPort.deleteExpiredRecords(any(Instant.class)))
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
        verify(savePiiAuditPort, times(3)).save(any());
    }

    @Test
    @DisplayName("Should_HandleVeryLargePiiCount_When_AuditingAccess")
    void Should_HandleVeryLargePiiCount_When_AuditingAccess() {
        // Given
        int largePiiCount = Integer.MAX_VALUE;

        // When
        auditService.auditPiiAccess("scan-123", AccessPurpose.USER_DISPLAY, largePiiCount);

        // Then
        verify(savePiiAuditPort).save(auditRecordCaptor.capture());
        assertThat(auditRecordCaptor.getValue().piiEntitiesCount())
            .isEqualTo(largePiiCount);
    }
}
