package pro.softcom.sentinelle.domain.pii.reporting;

/**
 * Domain model representing a revealed PII secret with its position and context.
 * 
 * <p>Business Rule: Secrets can only be revealed when explicitly authorized.</p>
 */
public record RevealedSecret(
        int startPosition,
        int endPosition,
        String sensitiveValue,
        String sensitiveContext,
        String maskedContext
) {
    public RevealedSecret {
        if (startPosition < 0) {
            throw new IllegalArgumentException("startPosition must be non-negative");
        }
        if (endPosition < startPosition) {
            throw new IllegalArgumentException("endPosition must be greater than or equal to startPosition");
        }
    }
}
