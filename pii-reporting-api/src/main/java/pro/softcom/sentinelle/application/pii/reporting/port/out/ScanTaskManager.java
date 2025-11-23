package pro.softcom.sentinelle.application.pii.reporting.port.out;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

/**
 * Output port for managing scan tasks decoupled from SSE connections.
 * 
 * <p>This port enables scans to continue executing independently of SSE connections.
 * Scans are identified by a unique ID and can have multiple simultaneous subscribers.</p>
 * 
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Start a new scan and return its identifier</li>
 *   <li>Allow subscription to an existing scan via its ID</li>
 *   <li>Pause/stop an active scan</li>
 *   <li>Manage scan lifecycle (automatic cleanup after 1 hour TTL)</li>
 * </ul>
 * 
 * <p><strong>Guarantees:</strong></p>
 * <ul>
 *   <li>A scan continues even if all SSE subscribers disconnect</li>
 *   <li>New subscribers can receive past events (replay buffer of 1000 events)</li>
 *   <li>Thread-safe for concurrent access</li>
 * </ul>
 * 
 * @since 1.0
 */
public interface ScanTaskManager {
    
    /**
     * Starts a new independent scan with the provided data stream.
     *
     * <p>The scan executes autonomously, decoupled from SSE connections.
     * Even if all SSE clients disconnect, the scan continues.</p>
     *
     * <p>The provided scan stream is subscribed independently and its events
     * are published via an internal Sink with a replay buffer. This completely
     * decouples scan execution from SSE subscribers.</p>
     *
     * @param scanId the unique identifier for this scan (non-null)
     * @param scanDataStream the reactive stream of scan results to manage (non-null)
     * @throws IllegalArgumentException if scanId or scanDataStream is null
     */
    void startScan(String scanId, Flux<ScanResult> scanDataStream);
    
    /**
     * Subscribes to an existing scan to receive its events.
     * 
     * <p>Allows multiple clients to subscribe to the same scan simultaneously.
     * Past events can be replayed thanks to the replay buffer (up to 1000 events).</p>
     * 
     * @param scanId the scan identifier (non-null)
     * @return a Flux of scan events
     * @throws IllegalArgumentException if scanId is null
     * @throws pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.ScanTaskManagerAdapter.ScanNotFoundException 
     *         if the scan does not exist or has been cleaned up
     */
    Flux<ScanResult> subscribeScan(String scanId);
    
    /**
     * Pauses an active scan.
     * 
     * <p>Stops the scan execution by disposing its reactive subscription.
     * This operation is definitive - the scan cannot be resumed.</p>
     * 
     * @param scanId the identifier of the scan to pause (non-null)
     * @return true if the scan was paused, false if the scan does not exist or is already completed
     * @throws IllegalArgumentException if scanId is null
     */
    boolean pauseScan(String scanId);
}
