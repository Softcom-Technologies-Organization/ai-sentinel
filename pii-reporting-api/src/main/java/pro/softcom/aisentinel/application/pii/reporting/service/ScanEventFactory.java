package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.usecase.DetectionReportingEventType;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory for creating scan event results. Business intent: Centralizes event creation logic to
 * ensure consistency across scan workflows.
 */
@RequiredArgsConstructor
public class ScanEventFactory {

    private final ConfluenceUrlProvider confluenceUrlProvider;
    private final PiiContextExtractor piiContextExtractor;
    private final SeverityCalculationService severityCalculationService;


    /**
     * Creates a scan start event.
     */
    public ConfluenceContentScanResult createStartEvent(String scanId, String spaceKey, int pagesTotal,
                                                        double progress) {
        return ConfluenceContentScanResult.builder()
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
    public ConfluenceContentScanResult createCompleteEvent(String scanId, String spaceKey) {
        return ConfluenceContentScanResult.builder()
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
    public ConfluenceContentScanResult createPageStartEvent(String scanId, String spaceKey, ConfluencePage page,
                                                            int pageIndex, int pagesTotal, double progress) {
        return ConfluenceContentScanResult.builder()
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
    public ConfluenceContentScanResult createPageCompleteEvent(String scanId, String spaceKey, ConfluencePage page,
                                                               double progress) {
        return ConfluenceContentScanResult.builder()
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
    public ConfluenceContentScanResult createEmptyPageItemEvent(
        String scanId, String spaceKey, ConfluencePage page, double progress
    ) {
        return ConfluenceContentScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ITEM.getLabel())
            .isFinal(true)
            .pageId(page.id())
            .pageTitle(page.title())
            .detectedPIIList(List.of())
            .nbOfDetectedPIIBySeverity(Map.of())
            .nbOfDetectedPIIByType(Map.of())
            .pageUrl(buildPageUrl(page.id()))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .severity(null)
            .build();
    }

    /**
     * Creates a page item event with PII detection results.
     */
    public ConfluenceContentScanResult createPageItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                           String content, ContentPiiDetection detection,
                                                           double progress) {
        List<DetectedPersonallyIdentifiableInformation> entities = mapToEntityList(detection,
                                                                                   content);
        Map<String, Integer> summary = calculateSeveritySummary(entities);
        Map<String, Integer> piiTypeSummary = calculatePiiTypeSummary(entities);
        PersonallyIdentifiableInformationSeverity severity = calculateHighestSeverity(entities);

        return ConfluenceContentScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ITEM.getLabel())
            .isFinal(true)
            .pageId(page.id())
            .pageTitle(page.title())
            .detectedPIIList(entities)
            .nbOfDetectedPIIBySeverity(summary)
            .nbOfDetectedPIIByType(piiTypeSummary)
            .sourceContent(content)
            .pageUrl(buildPageUrl(page.id()))
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .severity(severity)
            .build();
    }

    /**
     * Creates an attachment item event with PII detection results.
     */
    public ConfluenceContentScanResult createAttachmentItemEvent(String scanId, String spaceKey, ConfluencePage page,
                                                                 AttachmentInfo attachment, String content,
                                                                 ContentPiiDetection detection, double progress) {
        List<DetectedPersonallyIdentifiableInformation> entities = mapToEntityList(detection,
                                                                                   content);
        Map<String, Integer> summary = calculateSeveritySummary(entities);
        Map<String, Integer> piiTypeSummary = calculatePiiTypeSummary(entities);
        PersonallyIdentifiableInformationSeverity severity = calculateHighestSeverity(entities);

        return ConfluenceContentScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ATTACHMENT_ITEM.getLabel())
            .isFinal(true)
            .pageId(page.id())
            .pageTitle(page.title())
            .detectedPIIList(entities)
            .nbOfDetectedPIIBySeverity(summary)
            .nbOfDetectedPIIByType(piiTypeSummary)
            .sourceContent(content)
            .pageUrl(buildPageUrl(page.id()))
            .attachmentName(attachment.name())
            .attachmentType(attachment.mimeType())
            .attachmentUrl(attachment.url())
            .emittedAt(Instant.now().toString())
            .analysisProgressPercentage(progress)
            .scanStatus(ScanStatus.RUNNING)
            .severity(severity)
            .build();
    }

    /**
     * Creates an error event.
     */
    public ConfluenceContentScanResult createErrorEvent(String scanId, String spaceKey, String pageId,
                                                        String errorMessage, double progress) {
        return ConfluenceContentScanResult.builder()
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
    private List<DetectedPersonallyIdentifiableInformation> mapToEntityList(
        ContentPiiDetection detection, String content) {
        if (detection == null || detection.sensitiveDataFound() == null) {
            return List.of();
        }
        return detection.sensitiveDataFound().stream()
            .map(sensitiveData -> this.mapSensitiveDataToEntity(sensitiveData, content, detection))
            .toList();
    }

    private DetectedPersonallyIdentifiableInformation mapSensitiveDataToEntity(
        ContentPiiDetection.SensitiveData data, String sourceContent,
        ContentPiiDetection detection) {
        String type = (data.type() != null ? data.type().name() : null);
        String typeLabel = (data.type() != null ? data.type().getLabel() : null);
        // Build a lightweight list of entities to ensure other PIIs in the same line are also masked in context
        List<DetectedPersonallyIdentifiableInformation> all =
            detection == null || detection.sensitiveDataFound() == null ? List.of() :
                detection.sensitiveDataFound().stream()
                    .map(sd -> {
                        String sdType = null;
                        if (sd.type() != null) {
                            sdType = sd.type().name();
                        }
                        return DetectedPersonallyIdentifiableInformation.builder()
                            .startPosition(sd.position())
                            .endPosition(sd.end())
                            .piiType(sdType)
                            .build();
                    })
                    .toList();

        // Extract masked context (for immediate display, stored in clear)
        String maskedContext = piiContextExtractor.extractMaskedContext(sourceContent,
                                                                        data.position(), data.end(),
                                                                        type, all);

        // Extract real context (contains actual PII values, will be encrypted)
        String sensitiveContext = piiContextExtractor.extractSensitiveContext(sourceContent,
                                                                              data.position(),
                                                                              data.end());

        return DetectedPersonallyIdentifiableInformation.builder()
            .sensitiveContext(sensitiveContext)
            .maskedContext(maskedContext)
            .sensitiveValue(data.value())
            .piiType(type)
            .piiTypeLabel(typeLabel)
            .startPosition(data.position())
            .endPosition(data.end())
            .confidence(data.score())
            .source(data.source())
            .build();
    }

    /**
     * Calculates severity nbOfDetectedPIIBySeverity from detected PII entities. Business intent:
     * Provide severity-based counts (high, medium, low) for SSE real-time display. This ensures
     * consistency between SSE streaming and persisted data.
     *
     * @param entities List of detected PII entities
     * @return Map with keys "high", "medium", "low" and their respective counts
     */
    private Map<String, Integer> calculateSeveritySummary(
        List<DetectedPersonallyIdentifiableInformation> entities) {
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }

        var counts = severityCalculationService.aggregateCounts(entities);
        if (counts == null) {
            return Map.of();
        }

        return Map.of(
            "high", counts.high(),
            "medium", counts.medium(),
            "low", counts.low()
        );
    }

    /**
     * Calculates PII type nbOfDetectedPIIBySeverity from detected PII entities. Business intent:
     * Provide PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item detail display.
     *
     * @param entities List of detected PII entities
     * @return Map with PII type as key and count as value (e.g., {"EMAIL": 10, "CREDIT_CARD": 5})
     */
    private Map<String, Integer> calculatePiiTypeSummary(
        List<DetectedPersonallyIdentifiableInformation> entities) {
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }

        return entities.stream()
            .filter(entity -> entity.piiType() != null)
            .collect(
                Collectors.groupingBy(
                    DetectedPersonallyIdentifiableInformation::piiType,
                    Collectors.summingInt(_ -> 1)
                )
            );
    }

    /**
     * Calculates the highest severity from a list of PII entities. Business intent: Determine the
     * overall severity of a page/attachment based on the most severe PII type found.
     *
     * @param entities List of detected PII entities
     * @return Severity as string (HIGH, MEDIUM, LOW) or null if no entities
     */
    private PersonallyIdentifiableInformationSeverity calculateHighestSeverity(
        List<DetectedPersonallyIdentifiableInformation> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }

        PersonallyIdentifiableInformationSeverity highest = PersonallyIdentifiableInformationSeverity.LOW;
        for (DetectedPersonallyIdentifiableInformation entity : entities) {
            PersonallyIdentifiableInformationSeverity severity = severityCalculationService.calculateSeverity(entity.piiType());
            if (severity.compareTo(highest)
                < 0) {  // Lower ordinal = higher severity (HIGH=0, MEDIUM=1, LOW=2)
                highest = severity;
            }
        }
        return highest; // Returns "HIGH", "MEDIUM", or "LOW"
    }

    private String buildPageUrl(String pageId) {
        if (confluenceUrlProvider == null || pageId == null) {
            return null;
        }
        return confluenceUrlProvider.pageUrl(pageId);
    }
}