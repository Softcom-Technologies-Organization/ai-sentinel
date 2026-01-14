package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
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
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.GrpcPiiDetectorArmeriaClientAdapter;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

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
                .setType(
                    pro.softcom.aisentinel.infrastructure.confluence.adapter.out.PersonallyIdentifiableInformationType.EMAIL.name())
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
        assertThat(sd1.type()).isEqualTo(PersonallyIdentifiableInformationType.EMAIL);
        assertThat(sd1.value()).isEqualTo("john.doe@example.com");
        assertThat(sd1.context()).contains("5-25").contains("0.95");
        assertThat(sd1.position()).isEqualTo(5);
        assertThat(sd1.selector()).isEqualTo("pii-entity-" + pro.softcom.aisentinel.infrastructure.confluence.adapter.out.PersonallyIdentifiableInformationType.EMAIL.name().toLowerCase());

        ContentPiiDetection.SensitiveData sd2 = result.sensitiveDataFound().get(1);
        assertThat(sd2.type()).isEqualTo(PersonallyIdentifiableInformationType.UNKNOWN);
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
}
