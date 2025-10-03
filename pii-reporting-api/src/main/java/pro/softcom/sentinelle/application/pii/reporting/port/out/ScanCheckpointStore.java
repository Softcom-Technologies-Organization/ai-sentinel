package pro.softcom.sentinelle.application.pii.reporting.port.out;

/**
 * Administrative out-port for destructive operations on the scan checkpoint store (e.g., purge).
 */
public interface ScanCheckpointStore {
    void deleteAll();
}
