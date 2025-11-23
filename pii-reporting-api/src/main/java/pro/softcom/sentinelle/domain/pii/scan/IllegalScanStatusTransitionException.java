package pro.softcom.sentinelle.domain.pii.scan;

import pro.softcom.sentinelle.domain.pii.ScanStatus;

/**
 * Exception thrown when an illegal scan status transition is attempted.
 * 
 * <p>This exception is thrown when trying to transition a scan checkpoint
 * from one status to another in a way that violates business rules.
 * For example, trying to pause a completed scan.</p>
 */
public class IllegalScanStatusTransitionException extends RuntimeException {
    
    private final ScanStatus fromStatus;
    private final ScanStatus toStatus;
    private final Initiator initiator;
    
    public IllegalScanStatusTransitionException(
            ScanStatus fromStatus, 
            ScanStatus toStatus, 
            Initiator initiator) {
        super(String.format(
                "Illegal scan status transition from %s to %s (initiated by %s)", 
                fromStatus, 
                toStatus, 
                initiator));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.initiator = initiator;
    }
    
    public ScanStatus getFromStatus() {
        return fromStatus;
    }
    
    public ScanStatus getToStatus() {
        return toStatus;
    }
    
    public Initiator getInitiator() {
        return initiator;
    }
}
