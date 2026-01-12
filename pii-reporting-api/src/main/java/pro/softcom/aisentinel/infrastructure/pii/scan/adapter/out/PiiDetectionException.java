package pro.softcom.aisentinel.infrastructure.pii.scan.adapter.out;

/**
 * Base exception for PII detection service errors.
 * Uses sealed classes to provide type-safe error handling.
 */
public sealed class PiiDetectionException extends RuntimeException {

    protected PiiDetectionException(String message) {
        super(message);
    }

    protected PiiDetectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a connection exception when unable to connect to the PII detection service.
     */
    public static PiiDetectionConnectionException connectionError(String message, Throwable cause) {
        return new PiiDetectionConnectionException(message, cause);
    }

    /**
     * Creates a service exception when the PII detection service returns an error.
     */
    public static PiiDetectionServiceException serviceError(String message) {
        return new PiiDetectionServiceException(message);
    }

    /**
     * Creates a service exception when the PII detection service returns an error with a cause.
     */
    public static PiiDetectionServiceException serviceError(String message, Throwable cause) {
        return new PiiDetectionServiceException(message, cause);
    }

    /**
     * Creates a timeout exception when the PII detection service request times out.
     */
    public static PiiDetectionTimeoutException timeoutError(String message) {
        return new PiiDetectionTimeoutException(message);
    }

    /**
     * Exception thrown when unable to connect to the PII detection service.
     */
    public static final class PiiDetectionConnectionException extends PiiDetectionException {
        private PiiDetectionConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the PII detection service returns an error.
     */
    public static final class PiiDetectionServiceException extends PiiDetectionException {
        private PiiDetectionServiceException(String message) {
            super(message);
        }

        public PiiDetectionServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when the PII detection service request times out.
     */
    public static final class PiiDetectionTimeoutException extends PiiDetectionException {
        private PiiDetectionTimeoutException(String message) {
            super(message);
        }
    }
}
