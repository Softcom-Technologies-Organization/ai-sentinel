package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import lombok.Getter;

@Getter
public sealed class ConfluenceException extends RuntimeException permits ConfluenceApiException,
    ConfluenceAuthenticationException, ConfluenceConnectionException, ConfluenceNotFoundException,
    ConfluenceDateParseException {
    private final int statusCode;
    private final String confluenceMessage;
    
    public ConfluenceException(String message, int statusCode, String confluenceMessage) {
        super(message);
        this.statusCode = statusCode;
        this.confluenceMessage = confluenceMessage;
    }
    
    public ConfluenceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.confluenceMessage = null;
    }
}

final class ConfluenceAuthenticationException extends ConfluenceException {
    public ConfluenceAuthenticationException(String message, int statusCode) {
        super(message, statusCode, null);
    }
}

@Getter
final class ConfluenceNotFoundException extends ConfluenceException {
    private final String resourceId;
    
    public ConfluenceNotFoundException(String resourceId, String resourceType) {
        super(String.format("%s avec l'ID '%s' non trouvé", resourceType, resourceId), 404, null);
        this.resourceId = resourceId;
    }
}

final class ConfluenceConnectionException extends ConfluenceException {
    public ConfluenceConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception raised when the parsing of a date from the Confluence API fails.
 * This may indicate a change in the date format or corrupted data.
 */
@Getter
final class ConfluenceDateParseException extends ConfluenceException {
    private final String invalidDateString;
    
    public ConfluenceDateParseException(String invalidDateString, Throwable cause) {
        super(String.format("Échec du parsing du format de date Confluence: '%s'. " +
            "Cela peut indiquer un changement dans l'API Confluence.", invalidDateString), cause);
        this.invalidDateString = invalidDateString;
    }
}
