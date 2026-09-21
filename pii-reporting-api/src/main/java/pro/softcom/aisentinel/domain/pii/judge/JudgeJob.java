package pro.softcom.aisentinel.domain.pii.judge;

import lombok.Builder;

import java.time.Instant;

/**
 * Journal entry of an LLM judge run over the pending findings of one space. Progress counters
 * are updated after every verdict so a poller sees the run advance; no value or verdict detail
 * is kept here, those live on the finding rows.
 */
@Builder(toBuilder = true)
public record JudgeJob(
        String id,
        String spaceKey,
        JudgeJobStatus status,
        String model,
        JudgeApplyMode applyMode,
        double confidenceThreshold,
        int processed,
        int total,
        int falsePositives,
        int errors,
        String actor,
        Instant createdAt,
        Instant updatedAt
) {

    public JudgeJob {
        requireNonBlank(id, "id");
        requireNonBlank(spaceKey, "spaceKey");
        requireNonBlank(actor, "actor");
        requireNonNull(status, "status");
        requireNonNull(applyMode, "applyMode");
        requireNonNull(createdAt, "createdAt");
        requireNonNull(updatedAt, "updatedAt");
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be between 0 and 1");
        }
        requireNonNegative(processed, "processed");
        requireNonNegative(total, "total");
        requireNonNegative(falsePositives, "falsePositives");
        requireNonNegative(errors, "errors");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
