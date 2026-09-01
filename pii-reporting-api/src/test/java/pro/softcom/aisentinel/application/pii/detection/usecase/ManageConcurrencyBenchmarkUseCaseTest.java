package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ManageConcurrencyBenchmarkUseCase.
 */
@ExtendWith(MockitoExtension.class)
class ManageConcurrencyBenchmarkUseCaseTest {

    @Mock
    private PiiDetectionConfigRepository repository;

    @InjectMocks
    private ManageConcurrencyBenchmarkUseCase useCase;

    @Test
    void Should_DelegateToRepository_When_RequestBenchmarkCalled() {
        // Act
        useCase.requestBenchmark(8);

        // Assert
        verify(repository).requestBenchmark(8);
    }

    @Test
    void Should_RejectRequest_When_MaxConcurrencyOutOfRange() {
        assertThatThrownBy(() -> useCase.requestBenchmark(1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.requestBenchmark(21))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).requestBenchmark(anyInt());
    }

    @Test
    void Should_CancelInRepository_When_BenchmarkRunning() {
        when(repository.findBenchStatus()).thenReturn(
            new ConcurrencyBenchStatus("RUNNING", 40, "Testing concurrency 2/4", 2, "sig", 4)
        );

        useCase.cancelBenchmark();

        verify(repository).cancelBenchmark();
    }

    @Test
    void Should_NotCancel_When_NoBenchmarkInProgress() {
        when(repository.findBenchStatus()).thenReturn(
            new ConcurrencyBenchStatus("DONE", 100, "done", 4, "sig", 4)
        );

        useCase.cancelBenchmark();

        verify(repository, never()).cancelBenchmark();
    }

    @Test
    void Should_NotRerequest_When_BenchmarkAlreadyRunning() {
        // Arrange: a benchmark is already RUNNING.
        when(repository.findBenchStatus()).thenReturn(
            new ConcurrencyBenchStatus("RUNNING", 40, "Testing concurrency 2/4", 2, "sig", 4)
        );

        // Act
        useCase.requestBenchmark(4);

        // Assert: the request flag is NOT re-armed (no redundant second run).
        verify(repository, never()).requestBenchmark(anyInt());
    }

    @Test
    void Should_NotRerequest_When_BenchmarkPending() {
        // Arrange: a benchmark is already requested but not yet picked up.
        when(repository.findBenchStatus()).thenReturn(
            new ConcurrencyBenchStatus("PENDING", 0, "Starting benchmark", 1, null, 4)
        );

        // Act
        useCase.requestBenchmark(4);

        // Assert
        verify(repository, never()).requestBenchmark(anyInt());
    }

    @Test
    void Should_ReturnRepositoryStatus_When_GetBenchStatusCalled() {
        // Arrange
        ConcurrencyBenchStatus expectedStatus = new ConcurrencyBenchStatus(
            "RUNNING", 42, "probing 4 workers", 2, "localhost:1234|ministral", 4
        );
        when(repository.findBenchStatus()).thenReturn(expectedStatus);

        // Act
        ConcurrencyBenchStatus result = useCase.getBenchStatus();

        // Assert
        assertThat(result).isEqualTo(expectedStatus);
        verify(repository).findBenchStatus();
    }

    @Test
    void Should_ReturnIdleStatusUnchanged_When_BenchmarkNeverRan() {
        // Arrange
        ConcurrencyBenchStatus idleStatus = new ConcurrencyBenchStatus("IDLE", 0, null, 1, null, 4);
        when(repository.findBenchStatus()).thenReturn(idleStatus);

        // Act
        ConcurrencyBenchStatus result = useCase.getBenchStatus();

        // Assert
        assertThat(result.status()).isEqualTo("IDLE");
        assertThat(result.progress()).isZero();
        assertThat(result.message()).isNull();
        assertThat(result.concurrency()).isEqualTo(1);
        assertThat(result.tunedSignature()).isNull();
    }

    @Test
    void Should_PropagateException_When_RepositoryFailsToFlagRequest() {
        // Arrange
        doThrow(new RuntimeException("Database connection failed"))
            .when(repository).requestBenchmark(4);

        // Act & Assert
        assertThatThrownBy(() -> useCase.requestBenchmark(4))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection failed");
    }
}
