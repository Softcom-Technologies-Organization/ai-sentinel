package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Infrastructure adapter that implements the management of scan tasks decoupled from SSE.
 *
 * <p><strong>Business intent:</strong> Allows backend scans to continue running even when
 * browser clients disconnect. Uses Reactor Sinks as a publish-subscribe bridge between
 * the scan execution (independent subscription) and SSE consumers.</p>
 *
 * <p><strong>Key responsibilities:</strong></p>
 * <ul>
 *   <li>Start scans with an independent .subscribe() — not driven by downstream SSE subscribers</li>
 *   <li>Manage active scans in a thread-safe map (ConcurrentHashMap)</li>
 *   <li>Provide Flux&lt;ScanResult&gt; to SSE consumers via Sinks.asFlux()</li>
 *   <li>Support explicit pause via subscription.dispose()</li>
 *   <li>Periodically clean up completed scans (TTL: 1 hour)</li>
 * </ul>
 *
 * <p><strong>Architectural pattern:</strong> This component implements the solution to prevent
 * reactive cancellation propagation when SSE clients disconnect. The independent subscription
 * ensures the scan continues regardless of downstream subscribers' state.</p>
 *
 * <p><strong>Spring note:</strong> Uses @Service and @Scheduled because it lives in the
 * infrastructure layer where framework dependencies are allowed.</p>
 */
@Service
@Slf4j
public class ScanTaskManagerAdapter implements
    PersonallyIdentifiableInformationScanExecutionOrchestratorPort {
    
    private final Map<String, ManagedScan> managedScans = new ConcurrentHashMap<>();
    
    private static final int REPLAY_BUFFER_SIZE = 1000;
    private static final Duration CLEANUP_TTL = Duration.ofHours(1);

    /**
     * Represents an active managed scan with its sink, subscription, and lifecycle metadata.
     *
     * @param sink Reactor Sinks.Many used to publish scan events (replay buffer of 1000 events)
     * @param subscription Disposable handle to the independent scan execution
     * @param startTime Scan start instant
     * @param isCompleted AtomicBoolean flag indicating scan completion (success or error)
     */
    record ManagedScan(
            Sinks.Many<ConfluenceContentScanResult> sink,
            Disposable subscription,
            Instant startTime,
            AtomicBoolean isCompleted
    ) {}

    @Override
    public void startScan(String scanId, Flux<ConfluenceContentScanResult> scanDataStream) {
        if (scanId == null) {
            throw new IllegalArgumentException("scanId cannot be null");
        }
        if (scanDataStream == null) {
            throw new IllegalArgumentException("scanDataStream cannot be null");
        }
        
        log.info("[ScanTaskManager] Starting independent scan with scanId={}", scanId);
        
        // Create a sink with a replay buffer to support SSE reconnection (Last-Event-ID)
        Sinks.Many<ConfluenceContentScanResult> sink = Sinks.many().replay().limit(REPLAY_BUFFER_SIZE);
        
        // KEY: independent subscribe() — decouples scan execution from SSE subscribers' lifecycle
        // When the SSE client disconnects, this subscription keeps running
        // subscribeOn(boundedElastic) ensures the scan executes on a dedicated thread pool,
        // isolated from Virtual Thread interruptions when HTTP clients disconnect
        Disposable subscription = scanDataStream
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(event -> {
                    log.debug("[ScanTaskManager] Emitting event for scanId={}, eventType={}", 
                            scanId, event.eventType());
                    sink.emitNext(event, EmitFailureHandler.FAIL_FAST);
                })
                .doOnComplete(() -> {
                    log.info("[ScanTaskManager] Scan completed for scanId={}", scanId);
                    sink.emitComplete(EmitFailureHandler.FAIL_FAST);
                    markCompleted(scanId);
                })
                .doOnError(error -> {
                    log.error("[ScanTaskManager] Scan failed for scanId={}", scanId, error);
                    sink.emitError(error, EmitFailureHandler.FAIL_FAST);
                    markCompleted(scanId);
                })
                .doOnCancel(() -> {
                    log.info("[ScanTaskManager] Scan cancelled for scanId={}", scanId);
                    markCompleted(scanId);
                })
                .subscribe(); // ← KEY part — independent subscription on boundedElastic scheduler
        
        ManagedScan managedScan = new ManagedScan(
                sink,
                subscription,
                Instant.now(),
                new AtomicBoolean(false)
        );
        
        managedScans.put(scanId, managedScan);
        log.info("[ScanTaskManager] Scan registered, active scans count: {}", managedScans.size());

    }

    @Override
    public Flux<ConfluenceContentScanResult> subscribeScan(String scanId) {
        log.info("[ScanTaskManager] SSE client subscribing to scanId={}", scanId);
        
        ManagedScan scan = managedScans.get(scanId);
        if (scan == null) {
            log.warn("[ScanTaskManager] Scan not found: {}", scanId);
            return Flux.error(new ScanNotFoundException(
                    "Scan not found: " + scanId + ". It may have completed and been cleaned up."));
        }
        
        // Return a Flux from the sink — provides replay buffer for late subscribers
        return scan.sink().asFlux()
                .doOnSubscribe(_ -> log.debug("[ScanTaskManager] New subscriber for scanId={}", scanId))
                .doOnCancel(() -> log.debug("[ScanTaskManager] Subscriber cancelled for scanId={} " +
                        "(scan continues independently)", scanId));
    }

    @Override
    public boolean pauseScan(String scanId) {
        log.info("[ScanTaskManager] Pause requested for scanId={}", scanId);
        
        ManagedScan scan = managedScans.get(scanId);
        if (scan == null) {
            log.warn("[ScanTaskManager] Cannot pause - scan not found: {}", scanId);
            return false;
        }
        
        if (scan.subscription().isDisposed()) {
            log.info("[ScanTaskManager] Scan already disposed: {}", scanId);
            return false;
        }
        
        scan.subscription().dispose();
        markCompleted(scanId);
        log.info("[ScanTaskManager] Scan paused successfully: {}", scanId);
        return true;
    }


    /**
     * Marks a scan as completed (atomically).
     *
     * @param scanId Scan identifier
     */
    private void markCompleted(String scanId) {
        ManagedScan scan = managedScans.get(scanId);
        if (scan != null) {
            scan.isCompleted().set(true);
            log.debug("[ScanTaskManager] Marked scan as completed: {}", scanId);
        }
    }

    /**
     * Periodic cleanup of completed scans older than the TTL (1 hour).
     *
     * <p>Runs every 5 minutes. Removes completed scans older than CLEANUP_TTL to avoid memory
     * leaks while still allowing time for SSE reconnection.</p>
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void cleanupCompletedScans() {
        Instant cutoff = Instant.now().minus(CLEANUP_TTL);
        int initialSize = managedScans.size();
        
        managedScans.entrySet().removeIf(entry -> {
            String scanId = entry.getKey();
            ManagedScan scan = entry.getValue();
            
            boolean shouldRemove = scan.isCompleted().get() && scan.startTime().isBefore(cutoff);
            if (shouldRemove) {
                log.info("[ScanTaskManager] Cleaning up completed scan: {} (age: {})", 
                        scanId, Duration.between(scan.startTime(), Instant.now()));
                // Ensure the subscription is disposed
                if (!scan.subscription().isDisposed()) {
                    scan.subscription().dispose();
                }
            }
            return shouldRemove;
        });
        
        int removedCount = initialSize - managedScans.size();
        if (removedCount > 0) {
            log.info("[ScanTaskManager] Cleanup completed: removed {} scans, {} remaining", 
                    removedCount, managedScans.size());
        }
    }

    /**
     * Exception thrown when attempting to subscribe to a non-existent scan.
     */
    public static class ScanNotFoundException extends RuntimeException {
        public ScanNotFoundException(String message) {
            super(message);
        }
    }
}
