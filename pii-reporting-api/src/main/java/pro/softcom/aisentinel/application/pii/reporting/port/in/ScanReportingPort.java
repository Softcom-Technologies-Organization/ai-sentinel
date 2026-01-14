package pro.softcom.aisentinel.application.pii.reporting.port.in;

import java.util.List;
import java.util.Optional;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.scan.ConfluenceSpaceScanState;

public interface ScanReportingPort {

    Optional<LastScanMeta> getLatestScan();

    List<ConfluenceSpaceScanState> getLatestSpaceScanStateList(String scanId);

    List<ConfluenceContentScanResult> getLatestSpaceScanResultList();

    List<ConfluenceContentScanResult> getGlobalScanItemsEncrypted();

    /**
     * Returns a complete dashboard nbOfDetectedPIIBySeverity for the specified scan.
     * Combines authoritative state from scan_checkpoints with aggregated counters from scan_events.
     *
     * @param scanId the business identifier of the scan
     * @return an Optional containing the dashboard nbOfDetectedPIIBySeverity, or empty if scan not found
     */
    Optional<ScanReportingSummary> getScanReportingSummary(String scanId);

    /**
     * Returns a complete dashboard summary aggregating the latest state of all spaces across all scans.
     *
     * @return an Optional containing the dashboard summary, or empty if no data found
     */
    Optional<ScanReportingSummary> getGlobalScanSummary();
}