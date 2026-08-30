package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource.*;

/**
 * Armeria-based implementation of the PII detection client.
 * What: Uses an Armeria-provided gRPC blocking stub to call the Python gRPC service.
 * Why: Improves HTTP/2/gRPC client stability and observability while preserving domain API.
 *
 * <p><b>Observability</b>:
 * each gRPC call emits Micrometer metrics tagged {@code phase=grpc.client} and a
 * structured {@code [THROUGHPUT]} log. The emission is performed via
 * {@link Mono#subscribeOn(reactor.core.scheduler.Scheduler)} with
 * {@link Schedulers#parallel()} so the Reactor pipeline never blocks on
 * metric recording.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "armeria")
public class GrpcPiiDetectorArmeriaClientAdapter implements PiiDetectorClient {

    public static final String METRIC_CHARS_TOTAL = "pii.scan.chars.total";
    public static final String METRIC_DURATION = "pii.scan.duration";
    public static final String TAG_PHASE = "phase";
    public static final String TAG_PHASE_GRPC_CLIENT = "grpc.client";

    /**
     * Deadline for the health pre-flight, deliberately NOT the detection timeout
     * (30 min): this call gates a scan launch, so an unresponsive detection service
     * must surface in seconds instead of freezing the start. Comfortably above the
     * service-side endpoint probe budget.
     */
    private static final long HEALTH_CHECK_TIMEOUT_MS = 15_000L;

    private final PiiDetectorConfig config;
    private final PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub;
    private final MeterRegistry meterRegistry;

    public GrpcPiiDetectorArmeriaClientAdapter(PiiDetectorConfig config,
                                               PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub blockingStub,
                                               MeterRegistry meterRegistry) {
        this.config = config;
        this.blockingStub = blockingStub;
        this.meterRegistry = meterRegistry;
        log.info("PII Detection Service (Armeria) initialized - Host: {}, Port: {}", config.host(), config.port());
    }

    @Override
    public List<DetectorHealth> checkDetectorsHealth() {
        try {
            PiiDetection.DetectorsHealthResponse response = blockingStub
                    .withDeadlineAfter(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .checkDetectorsHealth(PiiDetection.DetectorsHealthRequest.getDefaultInstance());
            return response.getDetectorsList().stream()
                    .map(this::convertToDetectorHealth)
                    .toList();
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to check detectors health: %s", e.getMessage());
            throw PiiDetectionException.serviceError(errorMessage, e);
        }
    }

    private DetectorHealth convertToDetectorHealth(PiiDetection.DetectorHealth health) {
        return new DetectorHealth(
                convertToDetectorSource(health.getSource()),
                health.getReachable(),
                health.getEndpoint(),
                health.getError(),
                health.getErrorCode(),
                health.getErrorParamsMap());
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

        int charCount = content != null ? content.length() : 0;
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        try {
            PiiDetection.PIIDetectionRequest request = PiiDetection.PIIDetectionRequest.newBuilder()
                    .setContent(content)
                    .setThreshold(threshold)
                    .setFetchConfigFromDb(true)
                    .build();

            PiiDetection.PIIDetectionResponse response = blockingStub
                    .withDeadlineAfter(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                    .detectPII(request);

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            emitThroughputMetricsAsync(requestId, charCount, durationMs);

            log.debug("[Armeria] PII detection successful for PageId: {}", pageId);
            return convertToContentAnalysis(pageId, pageTitle, spaceKey, content, response);
        } catch (Exception e) {
            // Do not log and rethrow to avoid duplicate logs (Sonar rule S7717)
            final String errorMessage = String.format("Failed to analyze content for PII for pageId=%s: %s", pageId, e.getMessage());
            throw PiiDetectionException.serviceError(errorMessage, e);
        }
    }

    /**
     * Emits Micrometer counter + timer plus a structured {@code [THROUGHPUT]}
     * log line asynchronously to keep the Reactor pipeline non-blocking.
     *
     * <p>The throughput recording must NEVER add latency to the scan itself. We
     * schedule the emission on {@link Schedulers#parallel()} and never call
     * {@code block()} on the resulting {@link Mono}.
     */
    private void emitThroughputMetricsAsync(String requestId, int charCount, long durationMs) {
        Mono.fromRunnable(() -> recordThroughput(requestId, charCount, durationMs))
                .subscribeOn(Schedulers.parallel())
                .subscribe();
    }

    private void recordThroughput(String requestId, int charCount, long durationMs) {
        meterRegistry.counter(METRIC_CHARS_TOTAL, TAG_PHASE, TAG_PHASE_GRPC_CLIENT)
                .increment(charCount);
        meterRegistry.timer(METRIC_DURATION, TAG_PHASE, TAG_PHASE_GRPC_CLIENT)
                .record(durationMs, TimeUnit.MILLISECONDS);
        double charsPerSecond = durationMs > 0 ? (charCount * 1000.0) / durationMs : 0.0;
        log.info("[THROUGHPUT] phase=grpc.client request_id={} chars={} duration_ms={} chars_per_s={}",
                requestId,
                charCount,
                durationMs,
                String.format(Locale.ROOT, "%.2f", charsPerSecond));
    }

    // --- Mapping helpers (same behavior as the grpc-netty implementation) ---

    private ContentPiiDetection convertToContentAnalysis(String pageId, String pageTitle, String spaceKey,
                                                         String content, PiiDetection.PIIDetectionResponse response) {
        boolean hasSupplementaryChars = content != null
                && content.length() != content.codePointCount(0, content.length());

        List<ContentPiiDetection.SensitiveData> sensitiveDataList = response.getEntitiesList().stream()
                .map(entity -> convertToSensitiveData(entity, content, hasSupplementaryChars))
                .toList();

        List<ContentPiiDetection.DiscardedSensitiveData> discardedByPostfilter =
                response.getDiscardedEntitiesList().stream()
                        .map(discarded -> convertToDiscardedSensitiveData(discarded, content, hasSupplementaryChars))
                        .toList();

        Map<String, Integer> statistics = response.getSummaryMap();

        List<DetectorRunStat> detectorRunStats = response.getDetectorStatsList().stream()
                .map(this::convertToDetectorRunStat)
                .toList();

        return ContentPiiDetection.builder()
                .pageId(pageId)
                .pageTitle(pageTitle)
                .spaceKey(spaceKey)
                .analysisDate(LocalDateTime.now(ZoneId.systemDefault()))
                .sensitiveDataFound(sensitiveDataList)
                .statistics(statistics)
                .discardedByPostfilter(discardedByPostfilter)
                .detectorRunStats(detectorRunStats)
                .discoveredLabels(response.getDiscoveredLabelsMap())
                .build();
    }

    private DetectorRunStat convertToDetectorRunStat(PiiDetection.DetectorRunStats stats) {
        return new DetectorRunStat(
                convertToDetectorSource(stats.getSource()),
                stats.getDurationMs(),
                stats.getEntitiesFound(),
                stats.getEntitiesDiscarded(),
                stats.getError());
    }

    private ContentPiiDetection.DiscardedSensitiveData convertToDiscardedSensitiveData(
            PiiDetection.DiscardedPIIEntity discarded, String content, boolean hasSupplementaryChars) {
        return new ContentPiiDetection.DiscardedSensitiveData(
                convertToSensitiveData(discarded.getEntity(), content, hasSupplementaryChars),
                discarded.getJudgeVerdict(),
                (double) discarded.getJudgeConfidence(),
                discarded.getJudgeReason()
        );
    }

    private ContentPiiDetection.SensitiveData convertToSensitiveData(PiiDetection.PIIEntity entity,
                                                                      String content, boolean hasSupplementaryChars) {
        // Normalize to UPPER_SNAKE_CASE: zero-shot labels may contain spaces/hyphens
        String piiType = entity.getType().trim().toUpperCase()
                .replace(" ", "_").replace("-", "_");
        String typeLabel = resolveTypeLabel(piiType);

        int start = entity.getStart();
        int end = entity.getEnd();

        if (hasSupplementaryChars) {
            start = codePointIndexToCodeUnitIndex(content, start);
            end = codePointIndexToCodeUnitIndex(content, end);
        }

        final String context = String.format(Locale.ROOT, "Detected at startingPosition %d-%d (confidence: %.2f)",
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
        // Name-based mapping keeps the adapter forward-compatible.
        return switch (protoSource.name()) {
            case "PRESIDIO" -> PRESIDIO;
            case "REGEX" -> REGEX;
            case "MINISTRAL" -> MINISTRAL;
            case "POSTFILTER" -> POSTFILTER;
            default -> DetectorSource.UNKNOWN_SOURCE;
        };
    }
}
