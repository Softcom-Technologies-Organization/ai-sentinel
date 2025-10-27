package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.service.PiiMaskingUtils;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

import java.util.Comparator;
import java.util.List;

/**
 * Maps domain ScanResult (clean architecture) to presentation ScanEvent (DTO for SSE/JSON).
 * This keeps the domain independent from the web layer while preserving API contract.
 */
@Component
public class ScanResultToScanEventMapper {

    public ScanEventDto toDto(ScanResult scanResult) {
        if (scanResult == null) return null;
        String masked = scanResult.maskedContent();
        if (masked == null) {
            // Build a full masked source by replacing entities with their [TYPE] token.
            // Note: differs from PiiContextExtractor which returns a local line context per entity.
            masked = PiiMaskingUtils.buildMaskedContent(scanResult.sourceContent(), scanResult.detectedEntities());
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
                .detectedEntities(scanResult.detectedEntities())
                .summary(scanResult.summary())
                .maskedContent(masked)
                .message(scanResult.message())
                .pageUrl(scanResult.pageUrl())
                .emittedAt(scanResult.emittedAt())
                .attachmentName(scanResult.attachmentName())
                .attachmentType(scanResult.attachmentType())
                .attachmentUrl(scanResult.attachmentUrl())
                .analysisProgressPercentage(scanResult.analysisProgressPercentage())
                .build();
    }
}
