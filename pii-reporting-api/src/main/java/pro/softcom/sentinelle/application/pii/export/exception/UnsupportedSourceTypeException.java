package pro.softcom.sentinelle.application.pii.export.exception;

/**
 * Exception thrown when a source type is not supported for export context retrieval.
 */
public class UnsupportedSourceTypeException extends RuntimeException {
    public UnsupportedSourceTypeException(String sourceType) {
        super(String.format("Unsupported source type: '%s'", sourceType));
    }

    public UnsupportedSourceTypeException(String sourceType, Throwable cause) {
        super(String.format("Unsupported source type: '%s'", sourceType), cause);
    }
}
