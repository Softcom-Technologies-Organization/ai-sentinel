package pro.softcom.aisentinel.application.pii.export.exception;

/**
 * Exception thrown when an export context cannot be found for the given source.
 */
public class ExportContextNotFoundException extends RuntimeException {
    public ExportContextNotFoundException(String sourceType, String sourceIdentifier) {
        super(String.format("Export context not found for type '%s' and identifier '%s'", 
                sourceType, sourceIdentifier));
    }

    public ExportContextNotFoundException(String sourceType, String sourceIdentifier, Throwable cause) {
        super(String.format("Export context not found for type '%s' and identifier '%s'", 
                sourceType, sourceIdentifier), cause);
    }
}
