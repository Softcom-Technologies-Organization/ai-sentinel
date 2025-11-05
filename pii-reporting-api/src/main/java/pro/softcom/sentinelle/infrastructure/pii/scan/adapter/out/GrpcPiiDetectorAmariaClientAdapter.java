package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import pro.softcom.sentinelle.domain.pii.scan.PiiType;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

/**
 * Armeria-based implementation of the PII detection client.
 * What: Uses an Armeria-provided gRPC blocking stub to call the Python gRPC service.
 * Why: Improves HTTP/2/gRPC client stability and observability while preserving domain API.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "armeria")
public class GrpcPiiDetectorAmariaClientAdapter implements PiiDetectorClient {

    private final PiiDetectorConfig config;
    private final PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub;

    public GrpcPiiDetectorAmariaClientAdapter(PiiDetectorConfig config, PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub) {
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
                    .build();

            PiiDetection.PIIDetectionResponse response = blockingStub
                    .withDeadlineAfter(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                    .detectPII(request);

            log.debug("[Armeria] PII detection successful for PageId: {}", pageId);
            return convertToContentAnalysis(pageId, pageTitle, spaceKey, response);
        } catch (Exception e) {
            // Do not log and rethrow to avoid duplicate logs (Sonar rule S7717)
            final String errorMessage = String.format("Failed to analyze content for PII for pageId=%s: %s", pageId, e.getMessage());
            throw PiiDetectionException.serviceError(errorMessage, e);
        }
    }

    // --- Mapping helpers (same behavior as the grpc-netty implementation) ---

    private ContentPiiDetection convertToContentAnalysis(String pageId, String pageTitle, String spaceKey,
                                                         PiiDetection.PIIDetectionResponse response) {
        List<ContentPiiDetection.SensitiveData> sensitiveDataList = response.getEntitiesList().stream()
                .map(this::convertToSensitiveData)
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

    private ContentPiiDetection.SensitiveData convertToSensitiveData(PiiDetection.PIIEntity entity) {
        ContentPiiDetection.DataType dataType;
        try {
            PiiType piiType = PiiType.valueOf(entity.getType().trim().toUpperCase());
            dataType = piiType.dataType();
        } catch (Exception _) {
            dataType = ContentPiiDetection.DataType.UNKNOWN;
            log.warn("[Armeria] Unknown PII type: {}, mapping to UNKNOWN", entity.getType());
        }
        final String context = String.format("Detected at position %d-%d (confidence: %.2f)",
                entity.getStart(), entity.getEnd(), entity.getScore());
        return new ContentPiiDetection.SensitiveData(
                dataType,
                entity.getText(),
                context,
                entity.getStart(),
                entity.getEnd(),
                (double) entity.getScore(),
                String.format("pii-entity-%s", entity.getType().toLowerCase())
        );
    }
}
