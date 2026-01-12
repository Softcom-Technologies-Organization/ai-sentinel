package pro.softcom.aisentinel.infrastructure.config;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.PiiDetectionException;
import reactor.core.publisher.Hooks;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Configuration to handle dropped errors in reactive streams.
 * Business intent: Prevents scan interruption when SSE connections are cancelled
 * and ensures proper logging of unexpected errors that escape normal error handling.
 */
@Slf4j
@Configuration
public class ReactorErrorHandlerConfig {
    
    @PostConstruct
    public void configureReactorHooks() {
        Hooks.onErrorDropped(this::handleDroppedError);
    }

    // VisibleForTesting
    void handleDroppedError(Throwable throwable) {
        boolean isCancellation = throwable instanceof CancellationException;
        boolean isWrappedCancellation = isWrappedCancellation(throwable);
        boolean isGrpcCancelled = isGrpcStatus(throwable, Status.Code.CANCELLED);
        boolean isPiiDetectionCancelled = isPiiDetectionCancelled(throwable);
        boolean isGrpcDeadlineExceeded = isGrpcStatus(throwable, Status.Code.DEADLINE_EXCEEDED);

        if (isCancellation) {
            log.info("Scan cancelled (CancellationException): {}", throwable.getMessage());
        } else if (isWrappedCancellation) {
            log.info("HTTP request cancelled (CompletionException wrapping CancellationException): {}", throwable.getMessage());
        } else if (isGrpcCancelled) {
            log.info("gRPC call cancelled: {}", throwable.getMessage());
        } else if (isPiiDetectionCancelled) {
            log.info("PII detection cancelled (SSE disconnection): {}", throwable.getMessage());
        } else if (isGrpcDeadlineExceeded) {
            log.warn("gRPC DEADLINE_EXCEEDED dropped (should have been caught by onErrorResume): {}", throwable.getMessage());
        } else {
            // Any other dropped error is unexpected and should be investigated
            log.error("Unexpected error dropped in reactive stream", throwable);
        }
    }

    private boolean isWrappedCancellation(Throwable throwable) {
        return throwable instanceof CompletionException && throwable.getCause() instanceof CancellationException;
    }

    private boolean isGrpcStatus(Throwable throwable, Status.Code code) {
        return throwable instanceof StatusRuntimeException statusRuntimeException &&
                statusRuntimeException.getStatus().getCode() == code;
    }

    private boolean isPiiDetectionCancelled(Throwable throwable) {
        if (throwable instanceof PiiDetectionException.PiiDetectionServiceException) {
            Throwable cause = throwable.getCause();
            return isGrpcStatus(cause, Status.Code.CANCELLED);
        }
        return false;
    }

}