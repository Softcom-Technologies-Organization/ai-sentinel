package pro.softcom.aisentinel.infrastructure.config;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.PiiDetectionException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class ReactorErrorHandlerConfigTest {

    private final ReactorErrorHandlerConfig config = new ReactorErrorHandlerConfig();

    @Test
    void shouldHandleCancellationException() {
        assertThatCode(() -> config.handleDroppedError(new CancellationException("test")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleCompletionExceptionWrappingCancellationException() {
        CompletionException ex = new CompletionException(new CancellationException("test"));
        assertThatCode(() -> config.handleDroppedError(ex))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleGrpcCancelled() {
        StatusRuntimeException ex = Status.CANCELLED.asRuntimeException();
        assertThatCode(() -> config.handleDroppedError(ex))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandlePiiDetectionExceptionWrappingGrpcCancelled() {
        StatusRuntimeException cause = Status.CANCELLED.asRuntimeException();
        PiiDetectionException.PiiDetectionServiceException ex = 
            PiiDetectionException.serviceError("test", cause);
        assertThatCode(() -> config.handleDroppedError(ex))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleGrpcDeadlineExceeded() {
        StatusRuntimeException ex = Status.DEADLINE_EXCEEDED.asRuntimeException();
        assertThatCode(() -> config.handleDroppedError(ex))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleUnexpectedError() {
        assertThatCode(() -> config.handleDroppedError(new RuntimeException("unexpected")))
                .doesNotThrowAnyException();
    }
}