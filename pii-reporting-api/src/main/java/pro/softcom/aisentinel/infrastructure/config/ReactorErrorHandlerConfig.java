package pro.softcom.aisentinel.infrastructure.config;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.PiiDetectionException;
import reactor.core.publisher.Hooks;

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
        Hooks.onErrorDropped(throwable -> {
            // Cancellation errors are normal behavior when SSE connections are closed
            if (throwable instanceof CancellationException) {
                log.info("Scan cancelled (CancellationException): {}", throwable.getMessage());
                return;
            }
            
            // Handle CompletionException wrapping CancellationException (HTTP client cancellations)
            if (throwable instanceof CompletionException ce &&
                ce.getCause() instanceof CancellationException) {
                log.info("HTTP request cancelled (CompletionException wrapping CancellationException): {}",
                          throwable.getMessage());
                return;
            }
            
            // gRPC CANCELLED errors are normal when threads are interrupted during shutdown
            if (throwable instanceof StatusRuntimeException sre && 
                sre.getStatus().getCode() == Status.Code.CANCELLED) {
                log.info("gRPC call cancelled: {}", throwable.getMessage());
                return;
            }
            
            // Handle PiiDetectionServiceException wrapping gRPC CANCELLED
            // This occurs when SSE client disconnects while PII detection is in progress
            if (throwable instanceof PiiDetectionException.PiiDetectionServiceException pse) {
                Throwable cause = pse.getCause();
                if (cause instanceof StatusRuntimeException sre && 
                    sre.getStatus().getCode() == Status.Code.CANCELLED) {
                    log.info("PII detection cancelled (SSE disconnection): {}", pse.getMessage());
                    return;
                }
            }
            
            // gRPC DEADLINE_EXCEEDED should be caught by the reactive chain, not dropped here
            // If we see this, it means the error wasn't properly handled upstream
            if (throwable instanceof StatusRuntimeException sre && 
                sre.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                log.warn("gRPC DEADLINE_EXCEEDED dropped (should have been caught by onErrorResume): {}", 
                         throwable.getMessage());
                return;
            }
            
            // Any other dropped error is unexpected and should be investigated
            log.error("Unexpected error dropped in reactive stream", throwable);
        });
    }
}
