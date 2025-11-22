package pro.softcom.sentinelle.infrastructure.pii.scan.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for generating monotonic event IDs for scan events.
 * Business intent: Assigns unique, sequential IDs to each event in a scan stream,
 * enabling clients to track which events they've received and support reconnection
 * with event replay via the Last-Event-ID mechanism.
 * Thread-safe: Uses ConcurrentHashMap and AtomicLong for safe concurrent access.
 * 
 * <p>Note: This class is framework-agnostic (no Spring annotations) as it resides
 * in the application layer. It must be instantiated manually or via factory pattern.</p>
 */
@Slf4j
public class ScanEventSequencer {

    private final ConcurrentHashMap<String, AtomicLong> scanSequences;

    public ScanEventSequencer() {
        this.scanSequences = new ConcurrentHashMap<>();
    }

    /**
     * Generates the next sequential event ID for the given scan.
     * 
     * @param scanId The unique identifier of the scan
     * @return The next monotonic event ID (starts at 1 for new scans)
     */
    public long nextSequence(String scanId) {
        return scanSequences
                .computeIfAbsent(scanId, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Resets the sequence for a given scan, removing it from memory.
     * Should be called when a scan completes or is cleaned up.
     * 
     * @param scanId The unique identifier of the scan to reset
     */
    public void resetSequence(String scanId) {
        AtomicLong removed = scanSequences.remove(scanId);
        if (removed != null) {
            log.debug("[SEQUENCER] Reset sequence for scanId={}, last value was {}", 
                     scanId, removed.get());
        }
    }
}
