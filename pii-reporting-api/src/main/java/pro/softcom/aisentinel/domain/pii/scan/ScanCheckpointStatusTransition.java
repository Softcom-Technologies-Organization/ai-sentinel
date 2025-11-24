package pro.softcom.aisentinel.domain.pii.scan;

import java.util.Map;
import java.util.Set;
import pro.softcom.aisentinel.domain.pii.ScanStatus;

/**
 * Manages status transitions for scan checkpoints according to business rules.
 * <p>
 * Transition rules:
 * - COMPLETED: Final state, no transitions allowed
 * - FAILED: Can be restarted (→ RUNNING)
 * - RUNNING: Can be paused by USER (→ PAUSED), completed or failed by SYSTEM
 * - PAUSED: Can be resumed by USER (→ RUNNING)
 * - NOT_STARTED: Can be started by SYSTEM (→ RUNNING)
 * <p>
 * Business Rule Reference: BR-SCAN-001
 */
public class ScanCheckpointStatusTransition {

    private final ScanStatus currentStatus;
    private final Initiator initiator;

    /**
     * Represents a valid transition with allowed initiators.
     */
    private static class Transition {
        private final ScanStatus toStatus;
        private final Set<Initiator> allowedInitiators;

        private Transition(ScanStatus toStatus, Set<Initiator> allowedInitiators) {
            this.toStatus = toStatus;
            this.allowedInitiators = allowedInitiators;
        }
    }

    /**
     * Map of allowed transitions for each status.
     * The key is the current status, the value is the set of possible transitions.
     */
    private static final Map<ScanStatus, Set<Transition>> ALLOWED_TRANSITIONS = Map.of(
        // Final state: no transitions allowed
        ScanStatus.COMPLETED, Set.of(),
        
        // Failed: can be restarted by USER
        ScanStatus.FAILED, Set.of(
            new Transition(ScanStatus.RUNNING, Set.of(Initiator.USER))
        ),
        
        // Running: can be paused (USER), completed (SYSTEM) or failed (SYSTEM)
        ScanStatus.RUNNING, Set.of(
            new Transition(ScanStatus.PAUSED, Set.of(Initiator.USER)),
            new Transition(ScanStatus.COMPLETED, Set.of(Initiator.SYSTEM)),
            new Transition(ScanStatus.FAILED, Set.of(Initiator.SYSTEM))
        ),
        
        // Paused: can be resumed by USER or completed by SYSTEM (if async process finishes)
        ScanStatus.PAUSED, Set.of(
            new Transition(ScanStatus.RUNNING, Set.of(Initiator.USER)),
            new Transition(ScanStatus.COMPLETED, Set.of(Initiator.SYSTEM))
        ),
        
        // Not started: can be launched by SYSTEM
        ScanStatus.NOT_STARTED, Set.of(
            new Transition(ScanStatus.RUNNING, Set.of(Initiator.SYSTEM))
        )
    );

    /**
     * Creates a transition manager for a given status and initiator.
     *
     * @param currentStatus The current status of the scan checkpoint
     * @param initiator The initiator of the transition (USER or SYSTEM)
     */
    public ScanCheckpointStatusTransition(ScanStatus currentStatus, Initiator initiator) {
        this.currentStatus = currentStatus;
        this.initiator = initiator;
    }

    /**
     * Checks if a transition to the target status is allowed.
     *
     * @param toStatus The target status of the transition
     * @return true if the transition is allowed, false otherwise
     */
    public boolean isTransitionAllowed(ScanStatus toStatus) {
        // Transition to the same status always allowed (idempotence)
        if (currentStatus == toStatus) {
            return true;
        }

        Set<Transition> transitions = ALLOWED_TRANSITIONS.get(currentStatus);
        if (transitions == null) {
            return false;
        }

        return transitions.stream()
            .anyMatch(t -> t.toStatus == toStatus && t.allowedInitiators.contains(initiator));
    }

    /**
     * Performs the transition to the target status if allowed.
     *
     * @param toStatus The target status of the transition
     * @return The target status if the transition is allowed
     * @throws IllegalScanStatusTransitionException if the transition is not allowed
     */
    public ScanStatus transition(ScanStatus toStatus) {
        if (isTransitionAllowed(toStatus)) {
            return toStatus;
        }

        throw new IllegalScanStatusTransitionException(currentStatus, toStatus, initiator);
    }
}
