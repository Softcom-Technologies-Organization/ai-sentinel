package pro.softcom.aisentinel.domain.pii.scan;

/**
 * Represents who initiated a status transition for a scan checkpoint.
 * 
 * <p>This is used to enforce different transition rules based on whether
 * the transition was requested by a user action (e.g., pause button)
 * or by the system itself (e.g., scan completion, error handling).</p>
 */
public enum Initiator {
    /**
     * Transition initiated by a user action (e.g., clicking pause button).
     */
    USER,
    
    /**
     * Transition initiated by the system (e.g., automatic scan completion).
     */
    SYSTEM
}
