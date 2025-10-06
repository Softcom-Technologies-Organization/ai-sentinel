package pro.softcom.sentinelle.application.confluence.exception;

import lombok.Getter;

/**
 * Exception métier pour les erreurs de rafraîchissement du cache des espaces Confluence.
 * Encapsule les problèmes rencontrés lors de la synchronisation asynchrone du cache.
 */
@Getter
public sealed class ConfluenceSpaceCacheException extends RuntimeException {
    
    private final String operation;
    
    public ConfluenceSpaceCacheException(String message, String operation) {
        super(message);
        this.operation = operation;
    }
    
    public ConfluenceSpaceCacheException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }
}

/**
 * Exception levée lors de l'échec de récupération des espaces depuis l'API Confluence.
 */
final class ConfluenceSpaceCacheRefreshException extends ConfluenceSpaceCacheException {
    
    public ConfluenceSpaceCacheRefreshException(String message, Throwable cause) {
        super(message, "REFRESH_FROM_API", cause);
    }
}

/**
 * Exception levée lors de l'échec de sauvegarde des espaces en cache.
 */
@Getter
final class ConfluenceSpaceCachePersistenceException extends ConfluenceSpaceCacheException {
    
    private final int spaceCount;
    
    public ConfluenceSpaceCachePersistenceException(int spaceCount, Throwable cause) {
        super(String.format("Échec de la sauvegarde de %d espaces en cache", spaceCount), 
              "PERSIST_TO_CACHE", 
              cause);
        this.spaceCount = spaceCount;
    }
}
