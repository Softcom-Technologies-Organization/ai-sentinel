package pro.softcom.sentinelle.application.confluence.port.out;

import java.time.LocalDateTime;
import java.util.List;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

/**
 * Port sortant: persistance et récupération des espaces Confluence en cache.
 * Permet de stocker les espaces pour améliorer les performances de l'interface utilisateur.
 */
public interface ConfluenceSpaceRepository {

    /**
     * Récupère tous les espaces en cache.
     */
    List<ConfluenceSpace> findAll();

    /**
     * Sauvegarde ou met à jour une liste d'espaces en cache.
     */
    void saveAll(List<ConfluenceSpace> spaces);

    /**
     * Trouve les espaces dont la dernière mise à jour est antérieure à la date spécifiée.
     * Utilisé pour identifier les espaces nécessitant un rafraîchissement.
     */
    List<ConfluenceSpace> findStaleSpaces(LocalDateTime cutoffTime);
}
