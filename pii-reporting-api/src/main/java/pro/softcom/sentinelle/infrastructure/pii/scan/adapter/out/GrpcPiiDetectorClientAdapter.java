package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import pii_detection.PiiDetection;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorError;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import pro.softcom.sentinelle.domain.pii.scan.PiiType;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.transport.PiiGrpcTransport;

/**
 * Unified PII detection client service using a pluggable transport.
 * What: Delegates the RPC to a transport adapter and maps the response to domain objects.
 * Business intent: Keep domain-facing API stable while allowing transport selection via configuration.
 */
@Service
@ConditionalOnMissingBean(PiiDetectorClient.class)
public class GrpcPiiDetectorClientAdapter implements PiiDetectorClient {
    private static final Logger logger = LoggerFactory.getLogger(GrpcPiiDetectorClientAdapter.class);

    private final PiiDetectorConfig config;
    private final PiiGrpcTransport transport;

    public GrpcPiiDetectorClientAdapter(PiiDetectorConfig config, PiiGrpcTransport transport) {
        this.config = config;
        this.transport = transport;
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
        logger.debug("Analyzing content for PII - PageId: {}, Threshold: {}", pageId, threshold);
        try {
            PiiDetection.PIIDetectionResponse response = transport.detect(content, threshold, config.requestTimeoutMs());
            return toContentAnalysis(pageId, pageTitle, spaceKey, response);
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to analyze content for PII for pageId=%s : %s", pageId, e.getMessage());
            throw PiiDetectorError.serviceError(errorMessage, e);
        }
    }

    // --- Mapping helpers (centralized) ---

    private ContentPiiDetection toContentAnalysis(String pageId, String pageTitle, String spaceKey, PiiDetection.PIIDetectionResponse response) {
        List<ContentPiiDetection.SensitiveData> sensitiveDataList = response.getEntitiesList().stream()
                .map(this::toSensitiveData)
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

    private ContentPiiDetection.SensitiveData toSensitiveData(PiiDetection.PIIEntity entity) {
        ContentPiiDetection.DataType dataType;
        try {
            PiiType piiType = PiiType.valueOf(entity.getType().trim().toUpperCase());
            dataType = piiType.dataType();
        } catch (Exception _) {
            dataType = ContentPiiDetection.DataType.UNKNOWN;
            logger.warn("Unknown PII type: {}, mapping to UNKNOWN", entity.getType());
        }
        final String context = String.format("Detected at position %d-%d (confidence: %.2f)",
                entity.getStart(), entity.getEnd(), entity.getScore());
        return new ContentPiiDetection.SensitiveData(
                dataType,
                entity.getText(),
                context,
                entity.getStart(),
                entity.getEnd(),
                Double.valueOf(entity.getScore()),
                String.format("pii-entity-%s", entity.getType().toLowerCase())
        );
    }
}
