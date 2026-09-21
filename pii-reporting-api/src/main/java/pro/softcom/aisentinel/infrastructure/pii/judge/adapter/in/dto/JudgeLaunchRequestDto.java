package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto;

/**
 * Launch request; {@code model} null means the configured detection model and
 * {@code confidenceThreshold} null means the default threshold.
 */
public record JudgeLaunchRequestDto(
        JudgeScopeDto scope,
        String model,
        String applyMode,
        Double confidenceThreshold
) {
}
