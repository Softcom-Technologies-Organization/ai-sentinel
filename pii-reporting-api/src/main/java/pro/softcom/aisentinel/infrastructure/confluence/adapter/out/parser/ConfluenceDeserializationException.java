package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.parser;

/**
 * Exception levée lors de l'échec de la désérialisation d'une réponse Confluence.
 */
public class ConfluenceDeserializationException extends RuntimeException {
    
    public ConfluenceDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
