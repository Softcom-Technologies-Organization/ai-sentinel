package pro.softcom.sentinelle.application.pii.reporting.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.AccessPurpose;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

/**
 * Port sortant pour interroger les résultats de scan persistés.
 * Fournit des vues de lecture orientées présentation sans exposer d'entités JPA.
 */
public interface ScanResultQuery {

    /**
     * Retourne les métadonnées du dernier scan (si disponible).
     */
    Optional<LastScanMeta> findLatestScan();

    /**
     * Retourne les compteurs par espace pour un scan donné.
     * Cette méthode ne gère pas le statut fonctionnel (RUNNING/COMPLETED/etc.).
     * Le statut est enrichi dans le cas d'usage en combinant avec les checkpoints.
     */
    List<SpaceCounter> getSpaceCounters(String scanId);

    /**
     * Lists item events with ENCRYPTED PII data.
     * Use when PII values don't need to be viewed (statistics, dashboards without detail).
     * 
     * @param scanId scan identifier
     * @return list of scan results with encrypted PII
     */
    List<ScanResult> listItemEventsEncrypted(String scanId);
    
    /**
     * Lists item events with DECRYPTED PII data.
     * Automatically logs access for GDPR/nLPD compliance.
     * 
     * @param scanId scan identifier
     * @param pageId page ID
     * @param purpose access purpose (for audit trail)
     * @return list of scan results with decrypted PII
     */
    List<ScanResult> listItemEventsDecrypted(String scanId, String pageId, AccessPurpose purpose);

    List<ScanResult> listItemEventsByScanIdAndSpaceKey(String scanId, String spaceKey);

    /**
     * Projection de lecture (côté application) pour les compteurs par espace.
     */
    record SpaceCounter(String spaceKey, long pagesDone, long attachmentsDone, Instant lastEventTs) {}
}
