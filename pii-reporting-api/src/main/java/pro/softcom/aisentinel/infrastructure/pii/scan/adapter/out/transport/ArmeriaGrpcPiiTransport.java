package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.transport;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;

/**
 * Armeria-based gRPC transport for PII detection.
 * What: delegates to an Armeria-provided blocking stub; per-call deadlines only.
 */
@Service
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "armeria")
public class ArmeriaGrpcPiiTransport implements PiiGrpcTransport {
    private static final Logger log = LoggerFactory.getLogger(ArmeriaGrpcPiiTransport.class);


    private final PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub;

    public ArmeriaGrpcPiiTransport(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub) {
        this.stub = stub;
        log.info("Armeria gRPC transport initialized");
    }

    @Override
    public PiiDetection.PIIDetectionResponse detect(String content, float threshold, long timeoutMs) {
        PiiDetection.PIIDetectionRequest req = PiiDetection.PIIDetectionRequest.newBuilder()
                .setContent(content)
                .setThreshold(threshold)
                .build();
        return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).detectPII(req);
    }
}
