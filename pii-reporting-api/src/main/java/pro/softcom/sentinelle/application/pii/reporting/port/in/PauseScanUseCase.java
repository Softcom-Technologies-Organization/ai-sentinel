package pro.softcom.sentinelle.application.pii.reporting.port.in;

/**
 * Use case to pause a running scan.
 * Business intent: Mark all spaces in a scan as PAUSED so the UI displays correct status.
 */
public interface PauseScanUseCase {

    /**
     * Pauses a scan by updating all its space checkpoints to PAUSED status.
     *
     * @param scanId the scan identifier to pause
     */
    void pauseScan(String scanId);
}
