package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource.*;

/**
 * Armeria-based implementation of the PII detection client.
 * What: Uses an Armeria-provided gRPC blocking stub to call the Python gRPC service.
 * Why: Improves HTTP/2/gRPC client stability and observability while preserving domain API.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "armeria")
public class GrpcPiiDetectorArmeriaClientAdapter implements PiiDetectorClient {

    private final PiiDetectorConfig config;
    private final PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub;

    public GrpcPiiDetectorArmeriaClientAdapter(PiiDetectorConfig config, PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub) {
        this.config = config;
        this.blockingStub = blockingStub;
        log.info("PII Detection Service (Armeria) initialized - Host: {}, Port: {}", config.host(), config.port());
    }

    @Override
    public ContentPiiDetection analyzeContent(String content) {
        return analyzeContent(content, config.defaultThreshold());
    }

    @Override
    public ContentPiiDetection analyzeContent(String content, float threshold) {
        return analyzePageContent(null, null, null, content, threshold);
    }

    @Override
    public ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) {
        return analyzePageContent(pageId, pageTitle, spaceKey, content, config.defaultThreshold());
    }

    @Override
    public ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) {
        log.debug("[Armeria] Analyzing content for PII - PageId: {}, Threshold: {}", pageId, threshold);
        try {
            PiiDetection.PIIDetectionRequest request = PiiDetection.PIIDetectionRequest.newBuilder()
                    .setContent(content)
                    .setThreshold(threshold)
                    .setFetchConfigFromDb(true)
                    .build();

            PiiDetection.PIIDetectionResponse response = blockingStub
                    .withDeadlineAfter(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                    .detectPII(request);

            log.debug("[Armeria] PII detection successful for PageId: {}", pageId);
            return convertToContentAnalysis(pageId, pageTitle, spaceKey, content, response);
        } catch (Exception e) {
            // Do not log and rethrow to avoid duplicate logs (Sonar rule S7717)
            final String errorMessage = String.format("Failed to analyze content for PII for pageId=%s: %s", pageId, e.getMessage());
            throw PiiDetectionException.serviceError(errorMessage, e);
        }
    }

    // --- Mapping helpers (same behavior as the grpc-netty implementation) ---

    private ContentPiiDetection convertToContentAnalysis(String pageId, String pageTitle, String spaceKey,
                                                         String content, PiiDetection.PIIDetectionResponse response) {
        boolean hasSupplementaryChars = content != null
                && content.length() != content.codePointCount(0, content.length());

        List<ContentPiiDetection.SensitiveData> sensitiveDataList = response.getEntitiesList().stream()
                .map(entity -> convertToSensitiveData(entity, content, hasSupplementaryChars))
                .toList();

        Map<String, Integer> statistics = response.getSummaryMap();

        return ContentPiiDetection.builder()
                .pageId(pageId)
                .pageTitle(pageTitle)
                .spaceKey(spaceKey)
                .analysisDate(LocalDateTime.now())
                .sensitiveDataFound(sensitiveDataList)
                .statistics(statistics)
                .build();
    }

    private ContentPiiDetection.SensitiveData convertToSensitiveData(PiiDetection.PIIEntity entity,
                                                                      String content, boolean hasSupplementaryChars) {
        String piiType = entity.getType().trim().toUpperCase();
        String typeLabel = resolveTypeLabel(piiType);

        int start = entity.getStart();
        int end = entity.getEnd();

        if (hasSupplementaryChars) {
            start = codePointIndexToCodeUnitIndex(content, start);
            end = codePointIndexToCodeUnitIndex(content, end);
        }

        final String context = String.format(Locale.ROOT, "Detected at position %d-%d (confidence: %.2f)",
                start, end, entity.getScore());

        DetectorSource source = convertToDetectorSource(entity.getSource());

        return new ContentPiiDetection.SensitiveData(
                piiType,
                typeLabel,
                entity.getText(),
                context,
                start,
                end,
                (double) entity.getScore(),
                String.format("pii-entity-%s", entity.getType().toLowerCase()),
                source
        );
    }

    /**
     * Converts a Python code point index to a Java UTF-16 code unit index.
     * Python's len() counts Unicode code points, while Java's String.length() counts UTF-16 code units.
     * Supplementary characters (emoji, etc.) use 2 code units in Java but 1 code point in Python.
     */
    private int codePointIndexToCodeUnitIndex(String content, int codePointIndex) {
        return content.offsetByCodePoints(0, codePointIndex);
    }

    private String resolveTypeLabel(String piiType) {
        try {
            return PersonallyIdentifiableInformationType.valueOf(piiType).getLabel();
        } catch (IllegalArgumentException _) {
            log.debug("[Armeria] No known label for PII type: {}, using type as label", piiType);
            return piiType;
        }
    }

    private DetectorSource convertToDetectorSource(PiiDetection.DetectorSource protoSource) {
        if (protoSource == null) {
            return DetectorSource.UNKNOWN_SOURCE;
        }
        return switch (protoSource) {
            case GLINER -> GLINER;
            case PRESIDIO -> PRESIDIO;
            case REGEX -> REGEX;
            default -> DetectorSource.UNKNOWN_SOURCE;
        };
    }
}