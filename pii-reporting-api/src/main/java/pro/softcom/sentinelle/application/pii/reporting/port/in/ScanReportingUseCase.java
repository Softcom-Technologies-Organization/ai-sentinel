package pro.softcom.sentinelle.application.pii.reporting.port.in;

import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceSpaceScanState;

public interface ScanReportingUseCase {

    Optional<LastScanMeta> getLatestScan();

    List<ConfluenceSpaceScanState> getLatestSpaceScanStateList(String scanId);

    List<ScanResult> getLatestSpaceScanResultList();
}
