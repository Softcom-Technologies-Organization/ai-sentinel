package pro.softcom.sentinelle.infrastructure.config;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CancellationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
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
                log.debug("Scan cancelled (CancellationException): {}", throwable.getMessage());
                return;
            }
            
            // Handle CompletionException wrapping CancellationException (HTTP client cancellations)
            if (throwable instanceof java.util.concurrent.CompletionException ce && 
                ce.getCause() instanceof CancellationException) {
                log.debug("HTTP request cancelled (CompletionException wrapping CancellationException): {}", 
                          throwable.getMessage());
                return;
            }
            
            // gRPC CANCELLED errors are normal when threads are interrupted during shutdown
            if (throwable instanceof StatusRuntimeException sre && 
                sre.getStatus().getCode() == Status.Code.CANCELLED) {
                log.debug("gRPC call cancelled: {}", throwable.getMessage());
                return;
            }
            
            // Any other dropped error is unexpected and should be investigated
            log.error("Unexpected error dropped in reactive stream", throwable);
        });
    }
}
