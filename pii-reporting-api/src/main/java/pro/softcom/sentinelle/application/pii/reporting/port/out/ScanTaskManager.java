package pro.softcom.sentinelle.application.pii.reporting.port.out;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

/**
 * Port de sortie pour gérer les tâches de scan de manière découplée du SSE.
 * 
 * <p>Ce port permet de démarrer des scans qui continuent à s'exécuter indépendamment
 * des connexions SSE. Les scans sont identifiés par un ID unique et peuvent avoir
 * plusieurs souscripteurs simultanés.</p>
 * 
 * <p><strong>Responsabilités:</strong></p>
 * <ul>
 *   <li>Démarrer un nouveau scan et retourner son identifiant</li>
 *   <li>Permettre la souscription à un scan existant via son ID</li>
 *   <li>Mettre en pause/arrêter un scan actif</li>
 *   <li>Gérer le cycle de vie des scans (cleanup automatique)</li>
 * </ul>
 * 
 * <p><strong>Garanties:</strong></p>
 * <ul>
 *   <li>Un scan continue même si tous les souscripteurs SSE se déconnectent</li>
 *   <li>Les nouveaux souscripteurs peuvent recevoir les événements passés (replay buffer)</li>
 *   <li>Thread-safe pour accès concurrents</li>
 * </ul>
 * 
 * @since 1.0
 */
public interface ScanTaskManager {
    
    /**
     * Démarre un nouveau scan indépendant avec le flux de données fourni.
     *
     * <p>Le scan s'exécute de manière autonome, découplé des connexions SSE.
     * Même si tous les clients SSE se déconnectent, le scan continue.</p>
     *
     * <p>Le flux de scan fourni est souscrit de manière indépendante et ses événements
     * sont publiés via un Sink interne. Cela permet de découpler complètement l'exécution du scan
     * des souscripteurs SSE.</p>
     *
     * @param scanDataStream le flux réactif de résultats de scan à gérer (non null)
     * @throws IllegalArgumentException si scanDataStream est null
     * @throws IllegalStateException    si le scan ne peut pas être démarré
     */
    void startScan(String scanId, Flux<ScanResult> scanDataStream);
    
    /**
     * S'abonne à un scan existant pour recevoir ses événements.
     * 
     * <p>Permet à plusieurs clients de s'abonner au même scan simultanément.
     * Les événements passés peuvent être rejoués grâce au replay buffer.</p>
     * 
     * @param scanId l'identifiant du scan (non null)
     * @return un Flux d'événements du scan
     * @throws IllegalArgumentException si scanId est null
     * @throws java.util.NoSuchElementException si le scan n'existe pas ou est terminé
     */
    Flux<ScanResult> subscribeScan(String scanId);
    
    /**
     * Met en pause un scan actif.
     * 
     * <p>Arrête l'exécution du scan en disposant sa souscription réactive.
     * Cette opération est définitive - le scan ne peut pas être repris.</p>
     * 
     * @param scanId l'identifiant du scan à mettre en pause (non null)
     * @return true si le scan a été mis en pause, false si le scan n'existe pas ou est déjà terminé
     * @throws IllegalArgumentException si scanId est null
     */
    boolean pauseScan(String scanId);
}
