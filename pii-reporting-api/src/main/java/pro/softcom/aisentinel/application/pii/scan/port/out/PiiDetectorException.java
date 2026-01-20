package pro.softcom.aisentinel.application.pii.scan.port.out;

public class PiiDetectorException extends RuntimeException {

    public PiiDetectorException(String message, Throwable cause) {
        super(message, cause);
    }

    public static PiiDetectorException serviceError(String message, Throwable cause) {
        return new PiiDetectorException(message, cause);
    }
}
