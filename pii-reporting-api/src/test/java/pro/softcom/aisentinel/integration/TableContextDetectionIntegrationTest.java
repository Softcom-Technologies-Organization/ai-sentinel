package pro.softcom.aisentinel.integration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.application.pii.reporting.service.DetectionOffsetRemapper;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.SensitiveData;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.GrpcPiiDetectorArmeriaClientAdapter;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TableContextDetectionIntegrationTest {

    @Test
    void Should_PreserveOriginalOccurrences_When_EnrichedTextCrossesGrpcWithUnicode() throws Exception {
        var content = new HtmlContentParser().extractContent("""
                <p>😀 Contacts</p><table><tr><th>Service</th><th>Email</th></tr>
                <tr><td>Support</td><td>lea@example.org</td></tr>
                <tr><td>Administration</td><td>lea@example.org</td></tr></table>
                """);
        AtomicReference<String> received = new AtomicReference<>();
        Server server = ServerBuilder.forPort(0).addService(detector(received)).build().start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        var registry = new SimpleMeterRegistry();
        try {
            var client = new GrpcPiiDetectorArmeriaClientAdapter(
                    new PiiDetectorConfig("localhost", server.getPort(), 0.5f, 5000L, 5000L),
                    PIIDetectionServiceGrpc.newBlockingStub(channel), registry);

            var result = DetectionOffsetRemapper.remap(client.analyzeContent(content.analysisText()), content);

            assertThat(received.get()).contains("Email : lea@example.org", "Service : Administration");
            assertThat(result.sensitiveDataFound()).hasSize(2);
            assertThat(result.sensitiveDataFound()).extracting(SensitiveData::startingPosition)
                    .containsExactly(content.contextText().indexOf("lea@"), content.contextText().lastIndexOf("lea@"));
            for (SensitiveData finding : result.sensitiveDataFound()) {
                assertThat(content.contextText().substring(finding.startingPosition(), finding.endingPosition()))
                        .isEqualTo(finding.value());
            }
        } finally {
            registry.close();
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static PIIDetectionServiceGrpc.PIIDetectionServiceImplBase detector(AtomicReference<String> received) {
        return new PIIDetectionServiceGrpc.PIIDetectionServiceImplBase() {
            @Override
            public void detectPII(PiiDetection.PIIDetectionRequest request,
                                  StreamObserver<PiiDetection.PIIDetectionResponse> observer) {
                String text = request.getContent();
                received.set(text);
                observer.onNext(PiiDetection.PIIDetectionResponse.newBuilder()
                        .addEntities(entity(text, text.indexOf("lea@"), "lea@example.org"))
                        .addEntities(entity(text, text.lastIndexOf("lea@"), "lea@example.org"))
                        .addEntities(entity(text, text.indexOf("Email :"), "Email"))
                        .putSummary("EMAIL", 3).build());
                observer.onCompleted();
            }
        };
    }

    private static PiiDetection.PIIEntity entity(String content, int start, String value) {
        int codePointStart = content.codePointCount(0, start);
        return PiiDetection.PIIEntity.newBuilder().setType("EMAIL").setText(value)
                .setStart(codePointStart).setEnd(codePointStart + value.codePointCount(0, value.length()))
                .setScore(0.95f).setSource(PiiDetection.DetectorSource.MINISTRAL).build();
    }
}
