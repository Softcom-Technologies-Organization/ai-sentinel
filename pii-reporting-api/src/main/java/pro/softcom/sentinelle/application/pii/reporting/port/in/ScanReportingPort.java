package pro.softcom.sentinelle.application.pii.reporting.port.in;

import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceSpaceScanState;

public interface ScanReportingPort {

    Optional<LastScanMeta> getLatestScan();

    List<ConfluenceSpaceScanState> getLatestSpaceScanStateList(String scanId);

    List<ScanResult> getLatestSpaceScanResultList();

    /**
     * Returns a complete dashboard summary for the specified scan.
     * Combines authoritative state from scan_checkpoints with aggregated counters from scan_events.
     *
     * @param scanId the business identifier of the scan
     * @return an Optional containing the dashboard summary, or empty if scan not found
     */
    Optional<ScanReportingSummary> getScanReportingSummary(String scanId);
}
