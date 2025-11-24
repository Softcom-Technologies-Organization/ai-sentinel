package pro.softcom.aisentinel.application.pii.export;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.domain.pii.reporting.PiiEntity;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

@Slf4j
public class DetectionReportMapper {
    public List<DetectionReportEntry> toDetectionReportEntries(ScanResult scanResult) {
        if (!isValidResult(scanResult)) {
            return List.of();
        }

        return scanResult.detectedEntities().stream()
                .map(piiEntity -> mapPiiEntityToDetectionReportEntry(piiEntity, scanResult))
                .toList();
    }

    private boolean isValidResult(ScanResult result) {
        return result != null && result.detectedEntities() != null && !result.detectedEntities().isEmpty();
    }

    private DetectionReportEntry mapPiiEntityToDetectionReportEntry(PiiEntity piiEntity, ScanResult scanResult) {
        return DetectionReportEntry.builder()
                .scanId(scanResult.scanId())
                .spaceKey(scanResult.spaceKey())
                .emittedAt(scanResult.emittedAt())
                .pageTitle(scanResult.pageTitle())
                .pageUrl(scanResult.pageUrl())
                .attachmentName(scanResult.attachmentName())
                .attachmentUrl(scanResult.attachmentUrl())
                .maskedContext(piiEntity.maskedContext())
                .type(piiEntity.piiType())
                .typeLabel(piiEntity.piiTypeLabel())
                .confidenceScore(piiEntity.confidence())
                .build();
    }
}
