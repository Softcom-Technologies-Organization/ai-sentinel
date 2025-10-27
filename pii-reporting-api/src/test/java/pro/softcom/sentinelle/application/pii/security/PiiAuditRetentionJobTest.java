package pro.softcom.sentinelle.application.pii.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiAuditRetentionJob - Scheduled purge of expired audit logs")
class PiiAuditRetentionJobTest {

    @Mock
    private PiiAccessAuditService auditService;

    private PiiAuditRetentionJob retentionJob;

    @BeforeEach
    void setUp() {
        retentionJob = new PiiAuditRetentionJob(auditService);
    }

    // ========== purgeExpiredAuditLogs Tests ==========

    @Test
    @DisplayName("Should_CallPurgeExpiredLogs_When_JobExecutes")
    void Should_CallPurgeExpiredLogs_When_JobExecutes() {
        // Given
        when(auditService.purgeExpiredLogs()).thenReturn(10);

        // When
        retentionJob.purgeExpiredAuditLogs();

        // Then
        verify(auditService).purgeExpiredLogs();
    }

    @Test
    @DisplayName("Should_NotThrowException_When_ServiceThrowsException")
    void Should_NotThrowException_When_ServiceThrowsException() {
        // Given
        when(auditService.purgeExpiredLogs())
            .thenThrow(new RuntimeException("Service error"));

        // When/Then - Should not throw
        retentionJob.purgeExpiredAuditLogs();

        verify(auditService).purgeExpiredLogs();
    }

    @Test
    @DisplayName("Should_HandleZeroDeletedRecords_When_NothingToDelete")
    void Should_HandleZeroDeletedRecords_When_NothingToDelete() {
        // Given
        when(auditService.purgeExpiredLogs()).thenReturn(0);

        // When
        retentionJob.purgeExpiredAuditLogs();

        // Then
        verify(auditService).purgeExpiredLogs();
    }

    @Test
    @DisplayName("Should_HandleLargeNumberOfDeletedRecords_When_ManyExpired")
    void Should_HandleLargeNumberOfDeletedRecords_When_ManyExpired() {
        // Given
        when(auditService.purgeExpiredLogs()).thenReturn(1000);

        // When
        retentionJob.purgeExpiredAuditLogs();

        // Then
        verify(auditService).purgeExpiredLogs();
    }

    @Test
    @DisplayName("Should_CallServiceOnlyOnce_When_JobExecutes")
    void Should_CallServiceOnlyOnce_When_JobExecutes() {
        // Given
        when(auditService.purgeExpiredLogs()).thenReturn(5);

        // When
        retentionJob.purgeExpiredAuditLogs();

        // Then
        verify(auditService, times(1)).purgeExpiredLogs();
        verifyNoMoreInteractions(auditService);
    }

    @Test
    @DisplayName("Should_ExecuteMultipleTimes_When_JobIsScheduled")
    void Should_ExecuteMultipleTimes_When_JobIsScheduled() {
        // Given
        when(auditService.purgeExpiredLogs())
            .thenReturn(5)
            .thenReturn(3)
            .thenReturn(0);

        // When: Simulate multiple scheduled executions
        retentionJob.purgeExpiredAuditLogs();
        retentionJob.purgeExpiredAuditLogs();
        retentionJob.purgeExpiredAuditLogs();

        // Then
        verify(auditService, times(3)).purgeExpiredLogs();
    }

    @Test
    @DisplayName("Should_ContinueExecution_When_PreviousExecutionFailed")
    void Should_ContinueExecution_When_PreviousExecutionFailed() {
        // Given: First call fails, second succeeds
        when(auditService.purgeExpiredLogs())
            .thenThrow(new RuntimeException("First execution failed"))
            .thenReturn(10);

        // When
        retentionJob.purgeExpiredAuditLogs(); // First execution (fails)
        retentionJob.purgeExpiredAuditLogs(); // Second execution (succeeds)

        // Then
        verify(auditService, times(2)).purgeExpiredLogs();
    }
}
