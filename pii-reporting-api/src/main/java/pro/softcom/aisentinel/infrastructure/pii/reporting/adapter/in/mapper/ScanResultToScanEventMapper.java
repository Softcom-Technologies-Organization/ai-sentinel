package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

/**
 * Maps domain ScanResult (clean architecture) to presentation ScanEvent (DTO for SSE/JSON).
 * This keeps the domain independent from the web layer while preserving API contract.
 * 
 * <p>Security: if pii.reporting.allow-secret-reveal is false, sensitiveValue is masked
 * before sending to frontend via SSE.</p>
 */
@Component
@RequiredArgsConstructor
public class ScanResultToScanEventMapper {

    public ScanEventDto toDto(ScanResult scanResult) {
        if (scanResult == null) return null;
        // Mask sensitiveValue if reveal is not allowed
        List<DetectedPersonallyIdentifiableInformation> entities = scanResult.detectedPIIs();
        if (entities != null) {
            entities = entities.stream()
                    .map(e -> e.toBuilder()
                            .sensitiveValue(null)
                            .sensitiveContext(null)
                            .build())
                    .toList();
        }
        
        return ScanEventDto.builder()
                .scanId(scanResult.scanId())
                .spaceKey(scanResult.spaceKey())
                .eventType(ScanEventType.from(scanResult.eventType()))
                .isFinal(scanResult.isFinal())
                .pagesTotal(scanResult.pagesTotal())
                .pageIndex(scanResult.pageIndex())
                .pageId(scanResult.pageId())
                .pageTitle(scanResult.pageTitle())
                .detectedEntities(entities)
                .summary(scanResult.summary())
                .message(scanResult.message())
                .pageUrl(scanResult.pageUrl())
                .emittedAt(scanResult.emittedAt())
                .attachmentName(scanResult.attachmentName())
                .attachmentType(scanResult.attachmentType())
                .attachmentUrl(scanResult.attachmentUrl())
                .analysisProgressPercentage(scanResult.analysisProgressPercentage())
                .status(scanResult.scanStatus() != null ? scanResult.scanStatus().name() : null)
                .build();
    }
}
