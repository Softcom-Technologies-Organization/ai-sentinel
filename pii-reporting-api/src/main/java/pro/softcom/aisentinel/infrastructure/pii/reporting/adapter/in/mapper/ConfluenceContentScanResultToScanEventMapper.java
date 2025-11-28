package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ConfluenceContentScanResultEventDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

/**
 * Maps domain ScanResult (clean architecture) to presentation ScanEvent (DTO for SSE/JSON).
 * This keeps the domain independent from the web layer while preserving API contract.
 * 
 * <p>Security: sensitive values are ALWAYS masked in SSE events by delegating to the
 * domain method {@link DetectedPersonallyIdentifiableInformation#withMaskedSensitiveData()}.
 * This prevents accidental leaks via logs, monitoring, or network capture.</p>
 * 
 * <p>To reveal actual PII values, clients must use the dedicated /reveal endpoint
 * which respects the pii.reporting.allow-secret-reveal configuration.</p>
 */
@Component
@RequiredArgsConstructor
public class ConfluenceContentScanResultToScanEventMapper {

    public ConfluenceContentScanResultEventDto toDto(
        ConfluenceContentScanResult confluenceContentScanResult) {
        if (confluenceContentScanResult == null) return null;
        
        // Delegate masking to domain business rule
        List<DetectedPersonallyIdentifiableInformation> detectedPIIs = confluenceContentScanResult.detectedPIIList();
        if (detectedPIIs != null) {
            detectedPIIs = detectedPIIs.stream()
                    .map(DetectedPersonallyIdentifiableInformation::withMaskedSensitiveData)
                    .toList();
        }
        
        return ConfluenceContentScanResultEventDto.builder()
                .scanId(confluenceContentScanResult.scanId())
                .spaceKey(confluenceContentScanResult.spaceKey())
                .eventType(ScanEventType.from(confluenceContentScanResult.eventType()))
                .isFinal(confluenceContentScanResult.isFinal())
                .pagesTotal(confluenceContentScanResult.pagesTotal())
                .pageIndex(confluenceContentScanResult.pageIndex())
                .pageId(confluenceContentScanResult.pageId())
                .pageTitle(confluenceContentScanResult.pageTitle())
                .detectedPIIList(detectedPIIs)
                .nbOfDetectedPIIBySeverity(confluenceContentScanResult.nbOfDetectedPIIBySeverity())
                .nbOfDetectedPIIByType(confluenceContentScanResult.nbOfDetectedPIIByType())
                .message(confluenceContentScanResult.message())
                .pageUrl(confluenceContentScanResult.pageUrl())
                .emittedAt(confluenceContentScanResult.emittedAt())
                .attachmentName(confluenceContentScanResult.attachmentName())
                .attachmentType(confluenceContentScanResult.attachmentType())
                .attachmentUrl(confluenceContentScanResult.attachmentUrl())
                .analysisProgressPercentage(confluenceContentScanResult.analysisProgressPercentage())
                .status(
                    confluenceContentScanResult.scanStatus() != null ? confluenceContentScanResult.scanStatus().name() : null)
                .severity(confluenceContentScanResult.severity())
                .build();
    }
}
