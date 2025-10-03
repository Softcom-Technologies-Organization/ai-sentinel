package pro.softcom.sentinelle.application.pii.scan.port.out;

/**
 * Application-level exception for errors occurring during PII detection.
 * Business intent: hide infrastructure/transport exceptions from the application/API surface.
 */
public class PiiDetectorError extends RuntimeException {

    public PiiDetectorError(String message) {
        super(message);
    }

    public PiiDetectorError(String message, Throwable cause) {
        super(message, cause);
    }

    // Convenience factory methods for adapter-side mapping
    public static PiiDetectorError serviceError(String message) {
        return new PiiDetectorError(message);
    }

    public static PiiDetectorError serviceError(String message, Throwable cause) {
        return new PiiDetectorError(message, cause);
    }

    public static PiiDetectorError connectionError(String message, Throwable cause) {
        return new PiiDetectorError(message, cause);
    }

    public static PiiDetectorError timeoutError(String message) {
        return new PiiDetectorError(message);
    }
}
