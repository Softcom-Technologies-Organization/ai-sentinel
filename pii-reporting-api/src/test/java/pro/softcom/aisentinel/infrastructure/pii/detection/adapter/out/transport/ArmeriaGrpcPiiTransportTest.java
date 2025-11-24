package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.transport.ArmeriaGrpcPiiTransport;

@ExtendWith(MockitoExtension.class)
class ArmeriaGrpcPiiTransportTest {

    @Mock
    private PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub baseStub;

    @Mock
    private PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub deadlineStub;

    @Captor
    private ArgumentCaptor<PiiDetection.PIIDetectionRequest> requestCaptor;

    private ArmeriaGrpcPiiTransport transport;

    @BeforeEach
    void setUp() {
        transport = new ArmeriaGrpcPiiTransport(baseStub);
    }

    @Test
    @DisplayName("Should_DelegateToStubWithDeadlineAndReturnResponse_When_DetectCalled")
    void Should_DelegateToStubWithDeadlineAndReturnResponse_When_DetectCalled() {
        // Given
        long timeoutMs = 1234L;
        String content = "Some content to analyze";
        float threshold = 0.75f;

        PiiDetection.PIIDetectionResponse expected = PiiDetection.PIIDetectionResponse.newBuilder()
                .putSummary("ok", 1)
                .build();

        when(baseStub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)).thenReturn(deadlineStub);
        // detectPII will be called on the stub returned by withDeadlineAfter
        when(deadlineStub.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenReturn(expected);

        // When
        PiiDetection.PIIDetectionResponse actual = transport.detect(content, threshold, timeoutMs);

        // Then
        assertThat(actual).isSameAs(expected);
        verify(baseStub).withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);

        verify(deadlineStub).detectPII(requestCaptor.capture());
        PiiDetection.PIIDetectionRequest sent = requestCaptor.getValue();
        assertThat(sent.getContent()).isEqualTo(content);
        assertThat(sent.getThreshold()).isEqualTo(threshold);
    }
}
