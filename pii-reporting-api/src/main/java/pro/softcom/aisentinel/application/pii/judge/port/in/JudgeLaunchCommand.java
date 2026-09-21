package pro.softcom.aisentinel.application.pii.judge.port.in;

import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;

/**
 * Operator request to run the judge. {@code model} null means the LM Studio model configured
 * for detection.
 */
public record JudgeLaunchCommand(
        JudgeScope scope,
        String model,
        JudgeApplyMode applyMode,
        double confidenceThreshold,
        String actor
) {

    public JudgeLaunchCommand {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (applyMode == null) {
            throw new IllegalArgumentException("applyMode is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be between 0 and 1");
        }
    }
}
