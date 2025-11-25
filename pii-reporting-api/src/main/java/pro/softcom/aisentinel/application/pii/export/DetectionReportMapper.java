package pro.softcom.aisentinel.application.pii.export;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

@Slf4j
public class DetectionReportMapper {
    public List<DetectionReportEntry> toDetectionReportEntries(ScanResult scanResult) {
        if (!isValidResult(scanResult)) {
            return List.of();
        }

        return scanResult.detectedPIIs().stream()
                .map(piiEntity -> mapPiiEntityToDetectionReportEntry(piiEntity, scanResult))
                .toList();
    }

    private boolean isValidResult(ScanResult result) {
        return result != null && result.detectedPIIs() != null && !result.detectedPIIs().isEmpty();
    }

    private DetectionReportEntry mapPiiEntityToDetectionReportEntry(
        DetectedPersonallyIdentifiableInformation detectedPersonallyIdentifiableInformation, ScanResult scanResult) {
        return DetectionReportEntry.builder()
                .scanId(scanResult.scanId())
                .spaceKey(scanResult.spaceKey())
                .emittedAt(scanResult.emittedAt())
                .pageTitle(scanResult.pageTitle())
                .pageUrl(scanResult.pageUrl())
                .attachmentName(scanResult.attachmentName())
                .attachmentUrl(scanResult.attachmentUrl())
                .maskedContext(detectedPersonallyIdentifiableInformation.maskedContext())
                .type(detectedPersonallyIdentifiableInformation.piiType())
                .typeLabel(detectedPersonallyIdentifiableInformation.piiTypeLabel())
                .confidenceScore(detectedPersonallyIdentifiableInformation.confidence())
                .build();
    }
}
