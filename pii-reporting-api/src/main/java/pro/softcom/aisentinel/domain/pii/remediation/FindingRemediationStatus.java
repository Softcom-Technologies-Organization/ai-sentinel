package pro.softcom.aisentinel.domain.pii.remediation;

/**
 * Lifecycle status of a PII finding, persistent across scans.
 *
 * <p>Allowed transitions:</p>
 * <ul>
 *   <li>{@code PENDING -> REDACTED} (redaction job only)</li>
 *   <li>{@code PENDING -> MANUALLY_HANDLED | FALSE_POSITIVE}</li>
 *   <li>{@code PENDING -> PROPOSED_FALSE_POSITIVE} (LLM judge only)</li>
 *   <li>{@code PROPOSED_FALSE_POSITIVE -> FALSE_POSITIVE | PENDING} (reviewer confirms or keeps)</li>
 *   <li>{@code MANUALLY_HANDLED | FALSE_POSITIVE -> PENDING} (restoration)</li>
 *   <li>{@code REDACTED} is terminal (redaction is irreversible)</li>
 * </ul>
 */
public enum FindingRemediationStatus {
    PENDING,
    REDACTED,
    MANUALLY_HANDLED,
    FALSE_POSITIVE,
    PROPOSED_FALSE_POSITIVE;

    public boolean canTransitionTo(FindingRemediationStatus target) {
        return switch (this) {
            case PENDING -> target == REDACTED || target == MANUALLY_HANDLED || target == FALSE_POSITIVE
                    || target == PROPOSED_FALSE_POSITIVE;
            case PROPOSED_FALSE_POSITIVE -> target == FALSE_POSITIVE || target == PENDING;
            case MANUALLY_HANDLED, FALSE_POSITIVE -> target == PENDING;
            case REDACTED -> false;
        };
    }

    /**
     * Validates the transition and returns the target status.
     *
     * @throws IllegalStatusTransitionException when the transition violates the lifecycle rules
     */
    public FindingRemediationStatus transitionTo(FindingRemediationStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStatusTransitionException(this, target);
        }
        return target;
    }
}
