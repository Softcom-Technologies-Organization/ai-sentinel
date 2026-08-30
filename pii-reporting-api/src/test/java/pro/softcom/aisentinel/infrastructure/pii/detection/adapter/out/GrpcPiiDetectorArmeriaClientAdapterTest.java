package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.GrpcPiiDetectorArmeriaClientAdapter;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcPiiDetectorArmeriaClientAdapterTest {

    private static final String TARGET_LOGGER_NAME =
        "pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.GrpcPiiDetectorArmeriaClientAdapter";

    @Mock
    private PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub;

    @Captor
    private ArgumentCaptor<Long> timeoutCaptor;

    @Captor
    private ArgumentCaptor<TimeUnit> unitCaptor;

    @Captor
    private ArgumentCaptor<PiiDetection.PIIDetectionRequest> requestCaptor;

    private PiiDetectorConfig config;
    private MeterRegistry meterRegistry;
    private Logger adapterLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        // host/port mostly for logging; defaultThreshold and requestTimeoutMs are asserted in tests
        config = new PiiDetectorConfig("localhost", 50051, 0.42f, 1500L, 2000L);
        meterRegistry = new SimpleMeterRegistry();

        adapterLogger = (Logger) LoggerFactory.getLogger(TARGET_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger.addAppender(logAppender);
        adapterLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        adapterLogger.detachAppender(logAppender);
        logAppender.stop();
        meterRegistry.close();
    }

    @Test
    @DisplayName("analyzePageContent: maps entities and summary, uses deadline and returns ContentAnalysis")
    void analyzePageContent_success() {
        // Given
        PiiDetection.PIIEntity emailEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("EMAIL")
                .setText("john.doe@example.com")
                .setStart(5)
                .setEnd(25)
                .setScore(0.95f)
                .build();

        PiiDetection.PIIEntity unknownEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("mystery")
                .setText("???")
                .setStart(30)
                .setEnd(33)
                .setScore(0.12f)
                .build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(emailEntity)
                .addEntities(unknownEntity)
                .putSummary("EMAIL", 1)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("123", "Title", "SPACE", "content to analyze");

        // Then - verify deadline set
        verify(stub).withDeadlineAfter(timeoutCaptor.capture(), unitCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isEqualTo(config.requestTimeoutMs());
        assertThat(unitCaptor.getValue()).isEqualTo(TimeUnit.MILLISECONDS);

        verify(stub).detectPII(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getContent()).isEqualTo("content to analyze");
        assertThat(requestCaptor.getValue().getThreshold()).isEqualTo(0.42f);

        assertThat(result.pageId()).isEqualTo("123");
        assertThat(result.pageTitle()).isEqualTo("Title");
        assertThat(result.spaceKey()).isEqualTo("SPACE");
        assertThat(result.sensitiveDataFound()).hasSize(2);

        ContentPiiDetection.SensitiveData sd1 = result.sensitiveDataFound().getFirst();
        assertThat(sd1.type()).isEqualTo("EMAIL");
        assertThat(sd1.typeLabel()).isEqualTo("Email");
        assertThat(sd1.value()).isEqualTo("john.doe@example.com");
        assertThat(sd1.context()).contains("5-25").contains("0.95");
        assertThat(sd1.startingPosition()).isEqualTo(5);
        assertThat(sd1.selector()).isEqualTo("pii-entity-email");

        ContentPiiDetection.SensitiveData sd2 = result.sensitiveDataFound().get(1);
        assertThat(sd2.type()).isEqualTo("MYSTERY");
        assertThat(sd2.typeLabel()).isEqualTo("MYSTERY");
        assertThat(sd2.selector()).isEqualTo("pii-entity-mystery");

        assertThat(result.statistics()).containsEntry("EMAIL", 1);
        assertThat(result.analysisDate()).isNotNull();
    }

    @Test
    @DisplayName("Should_MapDiscoveredLabels_When_ResponseCarriesDiscoveredLabels")
    void Should_MapDiscoveredLabels_When_ResponseCarriesDiscoveredLabels() {
        // Given - response with open-vocabulary labels dropped by the Python gate
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .putDiscoveredLabels("VEHICLE_COLOR", 3)
                .putDiscoveredLabels("PET_NAME", 1)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("p", "t", "s", "payload");

        // Then - the discovered labels are surfaced on the domain model
        assertThat(result.discoveredLabels())
                .containsEntry("VEHICLE_COLOR", 3)
                .containsEntry("PET_NAME", 1);
    }

    @Test
    @DisplayName("Should_MapDetectorRunStatFailure_When_DetectorReportsAnError")
    void Should_MapDetectorRunStatFailure_When_DetectorReportsAnError() {
        // Given - Ministral ran for 30s, found nothing, and said why
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addDetectorStats(PiiDetection.DetectorRunStats.newBuilder()
                        .setSource(PiiDetection.DetectorSource.MINISTRAL)
                        .setDurationMs(30_000L)
                        .setEntitiesFound(0)
                        .setError("ConnectError: connection refused")
                        .build())
                .addDetectorStats(PiiDetection.DetectorRunStats.newBuilder()
                        .setSource(PiiDetection.DetectorSource.REGEX)
                        .setDurationMs(12L)
                        .setEntitiesFound(0)
                        .build())
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("p", "t", "s", "payload");

        // Then - both found zero entities, but only Ministral is reported as failed
        assertSoftly(softly -> {
            ContentPiiDetection.DetectorRunStat ministral = result.detectorRunStats().getFirst();
            softly.assertThat(ministral.failed()).isTrue();
            softly.assertThat(ministral.error()).isEqualTo("ConnectError: connection refused");
            softly.assertThat(result.detectorRunStats().get(1).failed()).isFalse();
        });
    }

    @Test
    @DisplayName("Should_MapUnreachableDetector_When_CheckDetectorsHealth")
    void Should_MapUnreachableDetector_When_CheckDetectorsHealth() {
        // Given
        PiiDetection.DetectorsHealthResponse response = PiiDetection.DetectorsHealthResponse.newBuilder()
                .addDetectors(PiiDetection.DetectorHealth.newBuilder()
                        .setSource(PiiDetection.DetectorSource.MINISTRAL)
                        .setReachable(false)
                        .setEndpoint("http://lmstudio:1234/v1")
                        .setError("ConnectError: connection refused")
                        .build())
                .addDetectors(PiiDetection.DetectorHealth.newBuilder()
                        .setSource(PiiDetection.DetectorSource.REGEX)
                        .setReachable(true)
                        .build())
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.checkDetectorsHealth(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        List<DetectorHealth> health = service.checkDetectorsHealth();

        // Then
        assertSoftly(softly -> {
            softly.assertThat(health).hasSize(2);
            DetectorHealth ministral = health.getFirst();
            softly.assertThat(ministral.source()).isEqualTo(ContentPiiDetection.DetectorSource.MINISTRAL);
            softly.assertThat(ministral.reachable()).isFalse();
            softly.assertThat(ministral.describeFailure())
                .contains("MINISTRAL", "http://lmstudio:1234/v1", "connection refused");
            softly.assertThat(health.get(1).reachable()).isTrue();
        });
    }

    @Test
    @DisplayName("Should_ReturnEmptyDiscoveredLabels_When_ResponseHasNone")
    void Should_ReturnEmptyDiscoveredLabels_When_ResponseHasNone() {
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        ContentPiiDetection result = service.analyzePageContent("p", "t", "s", "payload");

        assertThat(result.discoveredLabels()).isEmpty();
    }

    @Test
    @DisplayName("analyzeContent(String): delegates to default threshold and null metadata")
    void analyzeContent_delegatesToDefaultThresholdAndNulls() {
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        ContentPiiDetection result = service.analyzeContent("hello");

        verify(stub).detectPII(requestCaptor.capture());
        PiiDetection.PIIDetectionRequest req = requestCaptor.getValue();
        assertThat(req.getContent()).isEqualTo("hello");
        assertThat(req.getThreshold()).isEqualTo(config.defaultThreshold());

        assertThat(result.pageId()).isNull();
        assertThat(result.pageTitle()).isNull();
        assertThat(result.spaceKey()).isNull();
    }

    @Test
    @DisplayName("analyzePageContent(..., no threshold): uses config.defaultThreshold()")
    void analyzePageContent_usesDefaultThreshold() {
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        service.analyzePageContent("p1", "t", "s", "abc");

        verify(stub).detectPII(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getThreshold()).isEqualTo(config.defaultThreshold());
    }

    @Test
    @DisplayName("analyzeContent(String, float): delegates to analyzePageContent with threshold and null metadata")
    void analyzeContent_withThreshold_delegates() {
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        ContentPiiDetection result = service.analyzeContent("payload", 0.33f);

        verify(stub).detectPII(requestCaptor.capture());
        PiiDetection.PIIDetectionRequest req = requestCaptor.getValue();
        assertThat(req.getContent()).isEqualTo("payload");
        assertThat(req.getThreshold()).isEqualTo(0.33f);
        assertThat(result.pageId()).isNull();
        assertThat(result.pageTitle()).isNull();
        assertThat(result.spaceKey()).isNull();
    }

    @Test
    @DisplayName("Should_ConvertCodePointPositionsToCodeUnitPositions_When_ContentContainsSupplementaryChars")
    void Should_ConvertCodePointPositionsToCodeUnitPositions_When_ContentContainsSupplementaryChars() {
        // Content with emoji 🌱 (U+1F331, supplementary char = 2 UTF-16 code units, 1 Python code point)
        // Python sees: "hello🌱world" as len=11, "world" at code point positions 6-11
        // Java sees:   "hello🌱world" as length=12, "world" at code unit positions 7-12
        String content = "hello\uD83C\uDF31world email@test.com endingPosition";
        //                 01234  56    789...
        // Python code points: h=0, e=1, l=2, l=3, o=4, 🌱=5, w=6, o=7, r=8, l=9, d=10, ' '=11, e=12...
        // Java code units:    h=0, e=1, l=2, l=3, o=4, 🌱=5-6, w=7, o=8, r=9, l=10, d=11, ' '=12, e=13...

        // Python detects "email@test.com" at code point positions 12-26
        int pythonStart = 12;
        int pythonEnd = 26;

        // Expected Java positions: 13-27 (shifted by 1 due to surrogate pair)
        int expectedJavaStart = 13;
        int expectedJavaEnd = 27;

        // Verify our test content is correct
        assertThat(content.substring(expectedJavaStart, expectedJavaEnd)).isEqualTo("email@test.com");

        PiiDetection.PIIEntity entity = PiiDetection.PIIEntity.newBuilder()
                .setType("EMAIL")
                .setText("email@test.com")
                .setStart(pythonStart)
                .setEnd(pythonEnd)
                .setScore(0.99f)
                .build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(entity)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("p1", "title", "space", content);

        // Then - positions must be converted to Java code unit indices
        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.startingPosition()).as("start startingPosition").isEqualTo(expectedJavaStart);
            softly.assertThat(sd.endingPosition()).as("endingPosition startingPosition").isEqualTo(expectedJavaEnd);
            softly.assertThat(content.substring(sd.startingPosition(), sd.endingPosition())).as("extracted text").isEqualTo("email@test.com");
        });
    }

    @Test
    @DisplayName("Should_ConvertPositionsCorrectly_When_ContentContainsMultipleSupplementaryChars")
    void Should_ConvertPositionsCorrectly_When_ContentContainsMultipleSupplementaryChars() {
        // Two emoji before the PII: 🌱 and 🔑 (both supplementary, each = 2 code units in Java)
        // Python sees len("ab🌱cd🔑email@x.com") = 18
        // Java sees length = 20 (2 extra for 2 surrogate pairs)
        String content = "ab\uD83C\uDF31cd\uD83D\uDD11email@x.com";
        // Python code points: a=0, b=1, 🌱=2, c=3, d=4, 🔑=5, e=6, m=7, ...
        // Java code units:    a=0, b=1, 🌱=2-3, c=4, d=5, 🔑=6-7, e=8, m=9, ...

        // Python detects "email@x.com" at code point positions 6-17
        int pythonStart = 6;
        int pythonEnd = 17;

        // Expected Java: shifted by 2 (two supplementary chars before PII)
        int expectedJavaStart = 8;
        int expectedJavaEnd = 19;

        assertThat(content.substring(expectedJavaStart, expectedJavaEnd)).isEqualTo("email@x.com");

        PiiDetection.PIIEntity entity = PiiDetection.PIIEntity.newBuilder()
                .setType("EMAIL")
                .setText("email@x.com")
                .setStart(pythonStart)
                .setEnd(pythonEnd)
                .setScore(0.95f)
                .build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(entity)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        ContentPiiDetection result = service.analyzePageContent("p2", "title", "space", content);

        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.startingPosition()).as("start startingPosition").isEqualTo(expectedJavaStart);
            softly.assertThat(sd.endingPosition()).as("endingPosition startingPosition").isEqualTo(expectedJavaEnd);
            softly.assertThat(content.substring(sd.startingPosition(), sd.endingPosition())).as("extracted text").isEqualTo("email@x.com");
        });
    }

    @Test
    @DisplayName("Should_MapProtoDetectorSourceToDomain_When_EntitiesCarrySource")
    void Should_MapProtoDetectorSourceToDomain_When_EntitiesCarrySource() {
        // Given - one entity per known proto DetectorSource (proto currently exposes PRESIDIO/REGEX)
        PiiDetection.PIIEntity presidioEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("PHONE").setText("0612345678").setStart(10).setEnd(20).setScore(0.9f)
                .setSource(PiiDetection.DetectorSource.PRESIDIO).build();
        PiiDetection.PIIEntity regexEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("IP_ADDRESS").setText("127.0.0.1").setStart(30).setEnd(39).setScore(0.9f)
                .setSource(PiiDetection.DetectorSource.REGEX).build();
        PiiDetection.PIIEntity unknownEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("MYSTERY").setText("?").setStart(40).setEnd(41).setScore(0.1f)
                .setSource(PiiDetection.DetectorSource.UNKNOWN_SOURCE).build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(presidioEntity)
                .addEntities(regexEntity)
                .addEntities(unknownEntity)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("p", "t", "s", "payload");

        // Then - sources are mapped to the domain enum
        assertSoftly(softly -> {
            softly.assertThat(result.sensitiveDataFound().get(0).source())
                    .isEqualTo(ContentPiiDetection.DetectorSource.PRESIDIO);
            softly.assertThat(result.sensitiveDataFound().get(1).source())
                    .isEqualTo(ContentPiiDetection.DetectorSource.REGEX);
            softly.assertThat(result.sensitiveDataFound().get(2).source())
                    .isEqualTo(ContentPiiDetection.DetectorSource.UNKNOWN_SOURCE);
        });
    }

    @Test
    @DisplayName("Should_MapMinistralProtoSourceToDomain_When_EntityCarriesSource")
    void Should_MapMinistralProtoSourceToDomain_When_EntityCarriesSource() {
        // Given - one entity carrying the MINISTRAL proto source (enum value 8)
        PiiDetection.PIIEntity ministralEntity = PiiDetection.PIIEntity.newBuilder()
                .setType("EMAIL").setText("a@b.com").setStart(0).setEnd(7).setScore(0.9f)
                .setSource(PiiDetection.DetectorSource.MINISTRAL).build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(ministralEntity)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // When
        ContentPiiDetection result = service.analyzePageContent("p", "t", "s", "payload");

        // Then - the source is mapped to the MINISTRAL domain enum value
        assertThat(result.sensitiveDataFound().getFirst().source())
                .isEqualTo(ContentPiiDetection.DetectorSource.MINISTRAL);
    }

    @Test
    @DisplayName("Should_KeepPositionsUnchanged_When_ContentContainsOnlyBmpChars")
    void Should_KeepPositionsUnchanged_When_ContentContainsOnlyBmpChars() {
        // BMP-only content: no supplementary chars, positions stay the same
        String content = "Hello email@test.com world";

        PiiDetection.PIIEntity entity = PiiDetection.PIIEntity.newBuilder()
                .setType("EMAIL")
                .setText("email@test.com")
                .setStart(6)
                .setEnd(20)
                .setScore(0.95f)
                .build();

        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder()
                .addEntities(entity)
                .build();

        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        ContentPiiDetection result = service.analyzePageContent("p3", "title", "space", content);

        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.startingPosition()).as("start startingPosition").isEqualTo(6);
            softly.assertThat(sd.endingPosition()).as("endingPosition startingPosition").isEqualTo(20);
            softly.assertThat(content.substring(sd.startingPosition(), sd.endingPosition())).as("extracted text").isEqualTo("email@test.com");
        });
    }

    @Test
    @DisplayName("Should_IncrementCharsCounter_When_DetectPii")
    void Should_IncrementCharsCounter_When_DetectPii() {
        // Arrange
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);
        String payload = "abcdef"; // 6 chars

        // Act
        service.analyzeContent(payload);

        // Assert — async emission, wait until counter has been incremented
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                    assertThat(meterRegistry.counter(
                            GrpcPiiDetectorArmeriaClientAdapter.METRIC_CHARS_TOTAL,
                            GrpcPiiDetectorArmeriaClientAdapter.TAG_PHASE,
                            GrpcPiiDetectorArmeriaClientAdapter.TAG_PHASE_GRPC_CLIENT)
                        .count()).isEqualTo(payload.length()));
    }

    @Test
    @DisplayName("Should_RecordDurationTimer_When_DetectPii")
    void Should_RecordDurationTimer_When_DetectPii() {
        // Arrange
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // Act
        service.analyzeContent("hello");

        // Assert — timer must have recorded exactly one observation
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                    assertThat(meterRegistry.timer(
                            GrpcPiiDetectorArmeriaClientAdapter.METRIC_DURATION,
                            GrpcPiiDetectorArmeriaClientAdapter.TAG_PHASE,
                            GrpcPiiDetectorArmeriaClientAdapter.TAG_PHASE_GRPC_CLIENT)
                        .count()).isEqualTo(1L));
    }

    @Test
    @DisplayName("Should_EmitThroughputLog_When_DetectPii")
    void Should_EmitThroughputLog_When_DetectPii() {
        // Arrange
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // Act
        service.analyzeContent("abc");

        // Assert — async emission, wait until log is captured
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    List<ILoggingEvent> events = logAppender.list;
                    assertThat(events)
                            .as("[THROUGHPUT] log line should be emitted at INFO level")
                            .anySatisfy(event -> {
                                SoftAssertions softly = new SoftAssertions();
                                softly.assertThat(event.getLevel()).isEqualTo(Level.INFO);
                                softly.assertThat(event.getFormattedMessage())
                                        .contains("[THROUGHPUT]")
                                        .contains("phase=grpc.client")
                                        .contains("chars=3")
                                        .contains("duration_ms=")
                                        .contains("chars_per_s=");
                                softly.assertAll();
                            });
                });
    }

    @Test
    @DisplayName("Should_NotBlockGrpcCall_When_MetricsEmission")
    void Should_NotBlockGrpcCall_When_MetricsEmission() {
        // Arrange — the gRPC call returns immediately; the metric emission is
        // dispatched to a parallel scheduler. We assert that the synchronous
        // adapter call returns well below 100ms even though the (async) metric
        // emission may not have completed yet.
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service =
            new GrpcPiiDetectorArmeriaClientAdapter(config, stub, meterRegistry);

        // Act
        long startNanos = System.nanoTime();
        service.analyzeContent("payload");
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        // Assert — synchronous call returns fast; async metric emission does not gate it.
        assertThat(elapsedMs)
                .as("gRPC adapter must not block on metric emission")
                .isLessThan(100L);
    }
}
