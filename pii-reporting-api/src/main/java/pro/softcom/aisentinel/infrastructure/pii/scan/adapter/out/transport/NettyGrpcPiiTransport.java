package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.transport;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;

/**
 * Netty-based gRPC transport for PII detection calls.
 * What: manages channel lifecycle, basic resilience, and per-call deadlines.
 */
@Service
@ConditionalOnProperty(name = "pii-detector.client", havingValue = "grpc", matchIfMissing = true)
public class NettyGrpcPiiTransport implements PiiGrpcTransport {
    private static final Logger log = LoggerFactory.getLogger(NettyGrpcPiiTransport.class);

    private final PiiDetectorConfig config;
    private final ManagedChannel channel;
    private final PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub;
    private final Object lock = new Object();

    public NettyGrpcPiiTransport(PiiDetectorConfig config) {
        this.config = config;
        String host = normalizeHost(config.host());
        this.channel = ManagedChannelBuilder.forAddress(host, config.port())
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(4 * 1024 * 1024)
                .enableRetry()
                .maxRetryAttempts(3)
                .build();
        this.stub = PIIDetectionServiceGrpc.newBlockingStub(channel);
        log.info("Netty gRPC transport initialized - {}:{} (effective host: {})", config.host(), config.port(), host);
    }

    @Override
    public PiiDetection.PIIDetectionResponse detect(String content, float threshold, long timeoutMs) {
        PiiDetection.PIIDetectionRequest req = PiiDetection.PIIDetectionRequest.newBuilder()
                .setContent(content)
                .setThreshold(threshold)
                .build();
        try {
            synchronized (lock) {
                if (channel.getState(false) == ConnectivityState.TRANSIENT_FAILURE) {
                    log.warn("Channel in TRANSIENT_FAILURE → reset backoff");
                    channel.resetConnectBackoff();
                }
                return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).detectPII(req);
            }
        } catch (StatusRuntimeException e) {
            if (isUnknownService(e)) {
                log.warn("UNIMPLEMENTED unknown service — retrying once with a fresh channel");
                return retryOnceWithFreshChannel(content, threshold, timeoutMs);
            }
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            log.info("Netty gRPC transport channel shutdown completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while shutting down Netty gRPC channel", e);
        }
    }

    private boolean isUnknownService(StatusRuntimeException e) {
        return e.getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED
                && e.getStatus().getDescription() != null
                && e.getStatus().getDescription().toLowerCase().contains("unknown service pii_detection.piidetectionservice");
    }

    private PiiDetection.PIIDetectionResponse retryOnceWithFreshChannel(String content, float threshold, long timeoutMs) {
        ManagedChannel tmp = null;
        try {
            Thread.sleep(200L);
            tmp = ManagedChannelBuilder.forAddress(normalizeHost(config.host()), config.port())
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(4 * 1024 * 1024)
                    .enableRetry()
                    .maxRetryAttempts(3)
                    .build();
            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub s = PIIDetectionServiceGrpc.newBlockingStub(tmp);
            PiiDetection.PIIDetectionRequest req = PiiDetection.PIIDetectionRequest.newBuilder()
                    .setContent(content == null ? "" : content)
                    .setThreshold(threshold)
                    .build();
            return s.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).detectPII(req);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new StatusRuntimeException(io.grpc.Status.CANCELLED.withDescription("Interrupted during retry"));
        } finally {
            if (tmp != null) {
                try {
                    tmp.shutdown().awaitTermination(3, TimeUnit.SECONDS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "127.0.0.1";
        String h = host.trim();
        return ("localhost".equalsIgnoreCase(h)) ? "127.0.0.1" : h;
    }
}
