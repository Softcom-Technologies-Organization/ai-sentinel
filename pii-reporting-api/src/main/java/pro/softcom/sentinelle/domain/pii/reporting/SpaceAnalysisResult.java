package pro.softcom.sentinelle.domain.pii.reporting;

import java.time.LocalDateTime;
import java.util.List;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;

/**
 * Résultat d'analyse d'un espace Confluence.
 * Contient les analyses de toutes les pages de l'espace et les statistiques associées.
 */
public record SpaceAnalysisResult(
    String spaceKey,
    String spaceName,
    int totalPages,
    int analyzedPages,
    List<ContentPiiDetection> analyses,
    String riskLevel,
    LocalDateTime processedAt
) {
    
    public SpaceAnalysisResult {
        if (analyses == null) analyses = List.of();
        if (riskLevel == null) riskLevel = "AUCUN";
        if (processedAt == null) processedAt = LocalDateTime.now();
    }
    
    /**
     * Calcule le score de risque total de l'espace
     */
    public int getRiskScore() {
        return analyses.stream()
            .mapToInt(ContentPiiDetection::getRiskScore)
            .sum();
    }
}
