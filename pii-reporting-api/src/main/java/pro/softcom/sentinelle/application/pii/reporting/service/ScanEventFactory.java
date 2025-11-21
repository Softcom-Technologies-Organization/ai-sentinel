package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.pii.reporting.usecase.DetectionReportingEventType;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating scan event results.
 * Business intent: Centralizes event creation logic to ensure consistency across scan workflows.
 */
@RequiredArgsConstructor
public class ScanEventFactory {

    private final ConfluenceUrlProvider confluenceUrlProvider;
    private final PiiContextExtractor piiContextExtractor;


    /**
     * Creates a scan start event.
     */
    public ScanResult createStartEvent(String scanId, String spaceKey, int pagesTotal, double progress) {
        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.START.getLabel())
            .pagesTotal(pagesTotal)
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    /**
     * Creates a scan complete event.
     */
    public ScanResult createCompleteEvent(String scanId, String spaceKey) {
        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.COMPLETE.getLabel())
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(100.0)
            .scanStatus(ScanStatus.COMPLETED)
            .build();
    }

    /**
     * Creates a page start event.
     */
    public ScanResult createPageStartEvent(String scanId, String spaceKey, ConfluencePage page,
                                          int pageIndex, int pagesTotal, double progress) {
        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.PAGE_START.getLabel())
            .pagesTotal(pagesTotal)
            .pageIndex(pageIndex)
            .pageId(page.id())
            .pageTitle(page.title())
            .pageUrl(buildPageUrl(page.id()))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    /**
     * Creates a page complete event.
     */
    public ScanResult createPageCompleteEvent(String scanId, String spaceKey, ConfluencePage page,
                                             double progress) {
        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.PAGE_COMPLETE.getLabel())
            .pageId(page.id())
            .pageTitle(page.title())
            .pageUrl(buildPageUrl(page.id()))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    /**
     * Creates an empty page item event when no content is available.
     */
    public ScanResult createEmptyPageItemEvent(
            String scanId, String spaceKey, ConfluencePage page, double progress
    ) {
        return ScanResult.builder()
                .scanId(scanId)
                .spaceKey(spaceKey)
                .eventType(DetectionReportingEventType.ITEM.getLabel())
                .isFinal(true)
                .pageId(page.id())
                .pageTitle(page.title())
                .detectedEntities(List.of())
                .summary(Map.of())
                .pageUrl(buildPageUrl(page.id()))
                .emittedAt(Instant.now().toString())
                .analysisProgressPercentage(progress)
                .scanStatus(ScanStatus.RUNNING)
                .build();
    }

    /**
     * Creates a page item event with PII detection results.
     */
    public ScanResult createPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                         String content, ContentPiiDetection detection,
                                         double progress) {
        List<PiiEntity> entities = mapToEntityList(detection, content);
        Map<String, Integer> summary = extractSummary(detection);

        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ITEM.getLabel())
            .isFinal(true)
            .pageId(page.id())
            .pageTitle(page.title())
            .detectedEntities(entities)
            .summary(summary)
            .sourceContent(content)
            .pageUrl(buildPageUrl(page.id()))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    /**
     * Creates an attachment item event with PII detection results.
     */
    public ScanResult createAttachmentItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                               AttachmentInfo attachment, String content,
                                               ContentPiiDetection detection, double progress) {
        List<PiiEntity> entities = mapToEntityList(detection, content);
        Map<String, Integer> summary = extractSummary(detection);

        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ATTACHMENT_ITEM.getLabel())
            .isFinal(true)
            .pageId(page.id())
            .pageTitle(page.title())
            .detectedEntities(entities)
            .summary(summary)
            .sourceContent(content)
            .pageUrl(buildPageUrl(page.id()))
            .attachmentName(attachment.name())
            .attachmentType(attachment.mimeType())
            .attachmentUrl(attachment.url())
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .build();
    }

    /**
     * Creates an error event.
     */
    public ScanResult createErrorEvent(String scanId, String spaceKey, String pageId,
                                      String errorMessage, double progress) {
        return ScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ERROR.getLabel())
            .pageId(pageId)
            .message(errorMessage)
            .pageUrl(buildPageUrl(pageId))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.FAILED)
            .build();
    }

    /**
     * Maps PII detection results to entity list for event payload.
     */
    private List<PiiEntity> mapToEntityList(ContentPiiDetection detection, String content) {
        if (detection == null || detection.sensitiveDataFound() == null) {
            return List.of();
        }
        return detection.sensitiveDataFound().stream()
            .map(sensitiveData -> this.mapSensitiveDataToEntity(sensitiveData, content, detection))
            .toList();
    }

    private PiiEntity mapSensitiveDataToEntity(ContentPiiDetection.SensitiveData data, String sourceContent, ContentPiiDetection detection) {
        String type = (data.type() != null ? data.type().name() : null);
        String typeLabel = (data.type() != null ? data.type().getLabel() : null);
        // Build a lightweight list of entities to ensure other PIIs in the same line are also masked in context
        List<PiiEntity> all = detection == null || detection.sensitiveDataFound() == null ? List.of() :
                detection.sensitiveDataFound().stream()
                        .map(sd -> {
                            String sdType = null;
                            if (sd.type() != null) {
                                sdType = sd.type().name();
                            }
                            return PiiEntity.builder()
                                    .startPosition(sd.position())
                                    .endPosition(sd.end())
                                    .piiType(sdType)
                                    .build();
                        })
                        .toList();
        
        // Extract masked context (for immediate display, stored in clear)
        String maskedContext = piiContextExtractor.extractMaskedContext(sourceContent, data.position(), data.end(), type, all);
        
        // Extract real context (contains actual PII values, will be encrypted)
        String sensitiveContext = piiContextExtractor.extractSensitiveContext(sourceContent, data.position(), data.end());
        
        return PiiEntity.builder()
                .sensitiveContext(sensitiveContext)
                .maskedContext(maskedContext)
                .sensitiveValue(data.value())
                .piiType(type)
                .piiTypeLabel(typeLabel)
                .startPosition(data.position())
                .endPosition(data.end())
                .confidence(data.score())
                .build();
    }

    private Map<String, Integer> extractSummary(ContentPiiDetection detection) {
        if (detection == null || detection.statistics() == null) {
            return Map.of();
        }
        Map<String, Integer> summary = detection.statistics();
        return summary.isEmpty() ? null : summary;
    }

    private String buildPageUrl(String pageId) {
        if (confluenceUrlProvider == null || pageId == null) {
            return null;
        }
        return confluenceUrlProvider.pageUrl(pageId);
    }
}
