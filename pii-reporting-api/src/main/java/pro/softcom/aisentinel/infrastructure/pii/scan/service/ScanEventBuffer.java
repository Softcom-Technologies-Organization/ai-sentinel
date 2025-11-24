package pro.softcom.aisentinel.infrastructure.pii.scan.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

/**
 * Service responsible for buffering scan events in memory for replay on reconnection.
 * Business intent: Provides short-term event replay capability when clients reconnect
 * after temporary disconnections. Works in conjunction with database checkpoints for
 * long-term scan recovery.
 * Thread-safe: Uses ConcurrentHashMap and synchronized RingBuffer operations.
 * 
 * <p>Note: This class is framework-agnostic (no Spring annotations) as it resides
 * in the application layer. It must be instantiated manually or via factory pattern.</p>
 */
@Slf4j
public class ScanEventBuffer {

    private static final int DEFAULT_BUFFER_CAPACITY = 1000;
    
    private final ConcurrentHashMap<String, RingBuffer<BufferedEvent>> buffers;
    private final int bufferCapacity;

    public ScanEventBuffer() {
        this(DEFAULT_BUFFER_CAPACITY);
    }

    /**
     * Constructor for testing with custom capacity.
     */
    ScanEventBuffer(int capacity) {
        this.buffers = new ConcurrentHashMap<>();
        this.bufferCapacity = capacity;
    }

    /**
     * Stores an event in the buffer for the given scan.
     * 
     * @param scanId The unique identifier of the scan
     * @param eventId The monotonic event ID
     * @param event The scan result event to buffer
     */
    public void addEvent(String scanId, long eventId, ScanResult event) {
        RingBuffer<BufferedEvent> buffer = buffers.computeIfAbsent(
            scanId,
            k -> new RingBuffer<>(bufferCapacity)
        );
        
        BufferedEvent bufferedEvent = new BufferedEvent(eventId, event, Instant.now());
        buffer.add(bufferedEvent);
        
        log.trace("[BUFFER] Added event {} to buffer for scan {}", eventId, scanId);
    }

    /**
     * Retrieves all events after the given event ID for replay.
     * 
     * @param scanId The unique identifier of the scan
     * @param afterEventId The last event ID the client received
     * @return List of buffered events after the given ID, or empty list if none
     */
    public List<BufferedEvent> getEventsAfter(String scanId, long afterEventId) {
        RingBuffer<BufferedEvent> buffer = buffers.get(scanId);
        if (buffer == null) {
            return List.of();
        }
        
        List<BufferedEvent> events = buffer.stream()
            .stream().filter(e -> e.eventId() > afterEventId)
            .toList();
        
        log.debug("[BUFFER] Replay {} events after ID {} for scan {}", 
                 events.size(), afterEventId, scanId);
        
        return events;
    }

    /**
     * Clears the buffer for a given scan.
     * Should be called when a scan completes or is cleaned up.
     * 
     * @param scanId The unique identifier of the scan
     */
    public void clearBuffer(String scanId) {
        RingBuffer<BufferedEvent> removed = buffers.remove(scanId);
        if (removed != null) {
            log.debug("[BUFFER] Cleared buffer for scan {}", scanId);
        }
    }

    /**
     * Represents a buffered scan event with metadata.
     */
    public record BufferedEvent(long eventId, ScanResult event, Instant timestamp) {}

    /**
     * Thread-safe circular buffer implementation.
     * When capacity is reached, oldest events are overwritten.
     */
    static class RingBuffer<T> {
        private final Object[] buffer;
        private final int capacity;
        private int head = 0;
        private int size = 0;

        RingBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            this.capacity = capacity;
            this.buffer = new Object[capacity];
        }

        /**
         * Adds an item to the buffer, overwriting the oldest if full.
         */
        synchronized void add(T item) {
            buffer[head] = item;
            head = (head + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        }

        /**
         * Returns all items in the buffer in chronological order.
         */
        @SuppressWarnings("unchecked")
        synchronized List<T> stream() {
            List<T> items = new ArrayList<>(size);
            int start = (size == capacity) ? head : 0;
            
            for (int i = 0; i < size; i++) {
                int index = (start + i) % capacity;
                items.add((T) buffer[index]);
            }
            
            return items;
        }
    }
}
