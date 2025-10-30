package pro.softcom.sentinelle.application.pii.export.exception;

/**
 * Exception thrown when an export context cannot be found for the given source.
 */
public class ExportContextNotFoundException extends RuntimeException {
    public ExportContextNotFoundException(String sourceType, String sourceIdentifier) {
        super(String.format("Contexte d'export introuvable pour le type '%s' et l'identifiant '%s'", 
                sourceType, sourceIdentifier));
    }

    public ExportContextNotFoundException(String sourceType, String sourceIdentifier, Throwable cause) {
        super(String.format("Contexte d'export introuvable pour le type '%s' et l'identifiant '%s'", 
                sourceType, sourceIdentifier), cause);
    }
}
