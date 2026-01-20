package pro.softcom.aisentinel.application.confluence.exception;

import lombok.Getter;

@Getter
public class ConfluenceSpaceCacheException extends RuntimeException {
    
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
