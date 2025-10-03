package pro.softcom.sentinelle.domain.pii.reporting;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import pro.softcom.sentinelle.domain.pii.ScanStatus;

/**
 * Rapport de scan complet des espaces Confluence.
 * Contient les résultats d'analyse de tous les espaces et pages scannés.
 */
public record FullScanReport(
    String scanId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    ScanStatus status,
    int totalSpaces,
    int processedSpaces,
    int totalPages,
    int processedPages,
    List<SpaceAnalysisResult> spaceResults,
    Map<String, Integer> globalStatistics,
    List<String> errors
) { }
