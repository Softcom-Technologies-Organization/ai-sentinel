package pro.softcom.aisentinel.application.pii.reporting.port.in;

/**
 * Input port for pausing a running scan identified by its {@code scanId}.
 *
 * <p>Pausing transitions checkpoints to a PAUSED status so the scan can be resumed later without
 * losing progress. Calling this for an unknown or already-completed scan is a no-op.
 */
public interface PauseScanPort {

    /**
     * Requests the scan with the given identifier to pause at the next safe checkpoint.
     *
     * @param scanId unique identifier of the running scan
     */
    void pauseScan(String scanId);
}
