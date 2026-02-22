package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.GrpcPiiDetectorArmeriaClientAdapter;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcPiiDetectorArmeriaClientAdapterTest {

    @Mock
    private PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub;

    @Captor
    private ArgumentCaptor<Long> timeoutCaptor;

    @Captor
    private ArgumentCaptor<TimeUnit> unitCaptor;

    @Captor
    private ArgumentCaptor<PiiDetection.PIIDetectionRequest> requestCaptor;

    private PiiDetectorConfig config;

    @BeforeEach
    void setUp() {
        // host/port mostly for logging; defaultThreshold and requestTimeoutMs are asserted in tests
        config = new PiiDetectorConfig("localhost", 50051, 0.42f, 1500L, 2000L);
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

        // Unknown type to trigger UNKNOWN branch
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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

        // When
        ContentPiiDetection result = service.analyzePageContent("123", "Title", "SPACE", "content to analyze");

        // Then - verify deadline set
        verify(stub).withDeadlineAfter(timeoutCaptor.capture(), unitCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isEqualTo(config.requestTimeoutMs());
        assertThat(unitCaptor.getValue()).isEqualTo(TimeUnit.MILLISECONDS);

        // Verify request content and threshold
        verify(stub).detectPII(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getContent()).isEqualTo("content to analyze");
        assertThat(requestCaptor.getValue().getThreshold()).isEqualTo(0.42f);

        // Assert mapping
        assertThat(result.pageId()).isEqualTo("123");
        assertThat(result.pageTitle()).isEqualTo("Title");
        assertThat(result.spaceKey()).isEqualTo("SPACE");
        assertThat(result.sensitiveDataFound()).hasSize(2);

        ContentPiiDetection.SensitiveData sd1 = result.sensitiveDataFound().getFirst();
        assertThat(sd1.type()).isEqualTo("EMAIL");
        assertThat(sd1.typeLabel()).isEqualTo("Email");
        assertThat(sd1.value()).isEqualTo("john.doe@example.com");
        assertThat(sd1.context()).contains("5-25").contains("0.95");
        assertThat(sd1.position()).isEqualTo(5);
        assertThat(sd1.selector()).isEqualTo("pii-entity-email");

        ContentPiiDetection.SensitiveData sd2 = result.sensitiveDataFound().get(1);
        assertThat(sd2.type()).isEqualTo("MYSTERY");
        assertThat(sd2.typeLabel()).isEqualTo("MYSTERY");
        assertThat(sd2.selector()).isEqualTo("pii-entity-mystery");

        assertThat(result.statistics()).containsEntry("EMAIL", 1);
        assertThat(result.analysisDate()).isNotNull();
    }

    @Test
    @DisplayName("analyzeContent(String): delegates to default threshold and null metadata")
    void analyzeContent_delegatesToDefaultThresholdAndNulls() {
        // Minimal response
        PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.detectPII(any())).thenReturn(response);

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

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
        String content = "hello\uD83C\uDF31world email@test.com end";
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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

        // When
        ContentPiiDetection result = service.analyzePageContent("p1", "title", "space", content);

        // Then - positions must be converted to Java code unit indices
        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.position()).as("start position").isEqualTo(expectedJavaStart);
            softly.assertThat(sd.end()).as("end position").isEqualTo(expectedJavaEnd);
            softly.assertThat(content.substring(sd.position(), sd.end())).as("extracted text").isEqualTo("email@test.com");
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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

        ContentPiiDetection result = service.analyzePageContent("p2", "title", "space", content);

        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.position()).as("start position").isEqualTo(expectedJavaStart);
            softly.assertThat(sd.end()).as("end position").isEqualTo(expectedJavaEnd);
            softly.assertThat(content.substring(sd.position(), sd.end())).as("extracted text").isEqualTo("email@x.com");
        });
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

        GrpcPiiDetectorArmeriaClientAdapter service = new GrpcPiiDetectorArmeriaClientAdapter(config, stub);

        ContentPiiDetection result = service.analyzePageContent("p3", "title", "space", content);

        ContentPiiDetection.SensitiveData sd = result.sensitiveDataFound().getFirst();
        assertSoftly(softly -> {
            softly.assertThat(sd.position()).as("start position").isEqualTo(6);
            softly.assertThat(sd.end()).as("end position").isEqualTo(20);
            softly.assertThat(content.substring(sd.position(), sd.end())).as("extracted text").isEqualTo("email@test.com");
        });
    }
}
