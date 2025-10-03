package pro.softcom.sentinelle.application.pii.reporting.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
     * Liste les événements d'items (page/attachment) persistés pour un scan donné, dans l'ordre d'émission.
     * Ces événements permettent d'afficher les résultats à froid (sans stream SSE).
     */
    List<ScanResult> listItemEvents(String scanId);

    /**
     * Projection de lecture (côté application) pour les compteurs par espace.
     */
    record SpaceCounter(String spaceKey, long pagesDone, long attachmentsDone, Instant lastEventTs) {}
}
