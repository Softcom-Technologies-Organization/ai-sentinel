package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

/**
 * Out-port for appending scan events to the event store.
 * Infrastructure adapters (e.g., JPA) must implement this port.
 */
public interface ScanEventStore {
    /** Append one event to the event store. */
    void append(ScanResult event);
    void deleteAll();
}
