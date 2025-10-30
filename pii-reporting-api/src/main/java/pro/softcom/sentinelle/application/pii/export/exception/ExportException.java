package pro.softcom.sentinelle.application.pii.export.exception;

public class ExportException extends RuntimeException {
    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
