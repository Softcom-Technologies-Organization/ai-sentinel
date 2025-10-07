package pro.softcom.sentinelle.application.pii.scan.port.out;

/**
 * Application-level exception for errors occurring during PII detection.
 * Business intent: hide infrastructure/transport exceptions from the application/API surface.
 */
public class PiiDetectorException extends RuntimeException {

    public PiiDetectorException(String message) {
        super(message);
    }

    public PiiDetectorException(String message, Throwable cause) {
        super(message, cause);
    }

    // Convenience factory methods for adapter-side mapping
    public static PiiDetectorException serviceError(String message) {
        return new PiiDetectorException(message);
    }

    public static PiiDetectorException serviceError(String message, Throwable cause) {
        return new PiiDetectorException(message, cause);
    }

    public static PiiDetectorException connectionError(String message, Throwable cause) {
        return new PiiDetectorException(message, cause);
    }

    public static PiiDetectorException timeoutError(String message) {
        return new PiiDetectorException(message);
    }
}
