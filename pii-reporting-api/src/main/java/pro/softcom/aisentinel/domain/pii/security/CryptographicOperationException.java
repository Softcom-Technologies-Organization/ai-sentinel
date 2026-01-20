package pro.softcom.aisentinel.domain.pii.security;

public class CryptographicOperationException extends RuntimeException {
    
    public CryptographicOperationException(String message) {
        super(message);
    }
    
    public CryptographicOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
