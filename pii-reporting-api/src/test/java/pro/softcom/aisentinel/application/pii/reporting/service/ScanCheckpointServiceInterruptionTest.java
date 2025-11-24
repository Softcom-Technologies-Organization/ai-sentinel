package pro.softcom.aisentinel.application.pii.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

/**
 * Tests that ScanCheckpointService handles thread interruptions gracefully
 * to ensure checkpoint persistence survives SSE client disconnections.
 */
@ExtendWith(MockitoExtension.class)
class ScanCheckpointServiceInterruptionTest {

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @InjectMocks
    private ScanCheckpointService scanCheckpointService;

    @Test
    void Should_PersistCheckpoint_When_ThreadIsInterrupted() {
        // Given: A valid scan result with pageComplete event
        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .eventType("pageComplete")
                .pageId("page-456")
                .build();

        // Mock repository to verify save is called
        doAnswer(invocation -> {
            ScanCheckpoint checkpoint = invocation.getArgument(0);
            assertThat(checkpoint.scanId()).isEqualTo("scan-123");
            assertThat(checkpoint.spaceKey()).isEqualTo("TEST");
            assertThat(checkpoint.lastProcessedPageId()).isEqualTo("page-456");
            assertThat(checkpoint.scanStatus()).isEqualTo(ScanStatus.RUNNING);
            return checkpoint;
        }).when(scanCheckpointRepository).save(any(ScanCheckpoint.class));

        // Interrupt current thread to simulate SSE disconnection
        Thread.currentThread().interrupt();
        Thread.currentThread().interrupt(); // Set it again for the test

        // When: Persisting checkpoint with thread interrupted
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Checkpoint should be saved despite interruption
        verify(scanCheckpointRepository).save(any(ScanCheckpoint.class));

        // And: Thread interruption flag should be restored
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void Should_RestoreInterruptionFlag_When_PersistenceThrowsException() {
        // Given: A valid scan result
        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .eventType("pageComplete")
                .pageId("page-456")
                .build();

        // Mock repository to throw exception
        doAnswer(invocation -> {
            throw new RuntimeException("DB connection error");
        }).when(scanCheckpointRepository).save(any(ScanCheckpoint.class));

        // Interrupt current thread
        Thread.currentThread().interrupt();

        // When: Persisting checkpoint that throws exception
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Thread interruption flag should still be restored
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void Should_NotPersistCheckpoint_When_ScanResultIsNull() {
        // Given: Null scan result
        Thread.currentThread().interrupt();

        // When: Persisting null checkpoint
        scanCheckpointService.persistCheckpoint(null);

        // Then: No interaction with repository
        verify(scanCheckpointRepository, org.mockito.Mockito.never()).save(any());

        // And: Thread interruption flag should not be cleared
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void Should_HandleNonInterruptedThread_Normally() {
        // Given: A valid scan result and non-interrupted thread
        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .eventType("pageComplete")
                .pageId("page-456")
                .build();

        // Ensure thread is not interrupted
        Thread.interrupted(); // Clear any existing flag

        // When: Persisting checkpoint without interruption
        scanCheckpointService.persistCheckpoint(scanResult);

        // Then: Checkpoint should be saved normally
        verify(scanCheckpointRepository).save(any(ScanCheckpoint.class));

        // And: Thread should not be interrupted after
        assertThat(Thread.interrupted()).isFalse();
    }
}
