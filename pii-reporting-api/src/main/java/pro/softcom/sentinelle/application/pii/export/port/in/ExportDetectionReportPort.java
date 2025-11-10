package pro.softcom.sentinelle.application.pii.export.port.in;

import pro.softcom.sentinelle.domain.pii.export.SourceType;

/**
 * Port d'entrée pour l'export des rapports de détection.
 * Ce port définit le contrat que les adapters d'entrée peuvent utiliser pour déclencher l'export.
 */
public interface ExportDetectionReportPort {

    /**
     * Exporte le rapport de détection pour un scan et une source donnés.
     *
     * @param scanId           l'identifiant unique du scan
     * @param sourceType       le type de source
     * @param sourceIdentifier l'identifiant unique de la source (ex: clé d'espace)
     */
    void export(String scanId, SourceType sourceType, String sourceIdentifier);
}
