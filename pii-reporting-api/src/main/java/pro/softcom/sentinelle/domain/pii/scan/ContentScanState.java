package pro.softcom.sentinelle.domain.pii.scan;

import java.time.LocalDateTime;
import java.util.List;
import pro.softcom.sentinelle.domain.pii.reporting.FullScanReport;

/**
 * État d'un scan en cours ou terminé.
 * Utilisé pour la persistance et la reprise des scans interrompus.
 */
public record ContentScanState(
    String scanId,
    FullScanReport currentReport,
    List<String> remainingSpaceKeys,
    LocalDateTime lastCheckpoint
) {}
