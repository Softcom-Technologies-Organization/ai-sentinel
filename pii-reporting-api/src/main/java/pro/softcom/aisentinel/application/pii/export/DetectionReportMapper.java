package pro.softcom.aisentinel.application.pii.export;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

@Slf4j
public class DetectionReportMapper {
    public List<DetectionReportEntry> toDetectionReportEntries(
        ConfluenceContentScanResult confluenceContentScanResult) {
        if (!isValidResult(confluenceContentScanResult)) {
            return List.of();
        }

        return confluenceContentScanResult.detectedPIIList().stream()
                .map(piiEntity -> mapPiiEntityToDetectionReportEntry(piiEntity,
                                                                     confluenceContentScanResult))
                .toList();
    }

    private boolean isValidResult(ConfluenceContentScanResult result) {
        return result != null && result.detectedPIIList() != null && !result.detectedPIIList().isEmpty();
    }

    private DetectionReportEntry mapPiiEntityToDetectionReportEntry(
        DetectedPersonallyIdentifiableInformation detectedPersonallyIdentifiableInformation, ConfluenceContentScanResult confluenceContentScanResult) {
        return DetectionReportEntry.builder()
                .scanId(confluenceContentScanResult.scanId())
                .spaceKey(confluenceContentScanResult.spaceKey())
                .emittedAt(confluenceContentScanResult.emittedAt())
                .pageTitle(confluenceContentScanResult.pageTitle())
                .pageUrl(confluenceContentScanResult.pageUrl())
                .attachmentName(confluenceContentScanResult.attachmentName())
                .attachmentUrl(confluenceContentScanResult.attachmentUrl())
                .maskedContext(detectedPersonallyIdentifiableInformation.maskedContext())
                .type(detectedPersonallyIdentifiableInformation.piiType())
                .typeLabel(detectedPersonallyIdentifiableInformation.piiTypeLabel())
                .confidenceScore(detectedPersonallyIdentifiableInformation.confidence())
                .build();
    }
}
