package pro.softcom.aisentinel.application.pii.reporting.port.in;

/**
 * Use case: purge all persisted data from previous scans.
 * Business purpose: called right before a new multi-space scan to start from a clean slate.
 */
public interface PurgeDetectionDataPort {
    /** Purge all scan-related persisted data. */
    void purgeAll();
}
