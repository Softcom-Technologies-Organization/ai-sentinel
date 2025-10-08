package pro.softcom.sentinelle.application.pii.reporting.port.in;

import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ConfluenceScanSpaceStatus;

public interface ScanReportingUseCase {

    Optional<LastScanMeta> getLatestScan();

    List<ConfluenceScanSpaceStatus> getLatestSpaceScanStateList(String scanId);

    /**
     * Retourne les événements d'items (page/attachment) du dernier scan pour affichage à froid.
     */
    List<ScanResult> getLatestScanItems();
}
