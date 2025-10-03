package pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.transport;

import pii_detection.PiiDetection;

/**
 * Technical port for invoking the Python gRPC PII detection service.
 * What: Executes the detectPII RPC and returns the raw gRPC response.
 * Business intent: Isolate transport concerns (channel, deadlines, retries) from the domain service.
 */
public interface PiiGrpcTransport {
    /**
     * Calls the remote detectPII method.
     *
     * @param content   content to analyze (may be empty but not null depending on implementation)
     * @param threshold confidence threshold to apply server-side
     * @param timeoutMs per-call deadline in milliseconds
     * @return gRPC response with entities and summary
     */
    PiiDetection.PIIDetectionResponse detect(String content, float threshold, long timeoutMs);
}
