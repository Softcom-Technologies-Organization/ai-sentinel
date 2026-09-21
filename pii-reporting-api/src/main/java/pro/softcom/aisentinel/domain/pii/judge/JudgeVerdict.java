package pro.softcom.aisentinel.domain.pii.judge;

import java.util.Arrays;
import java.util.Optional;

/**
 * Verdict of the LLM judge on a finding. A stored {@code FALSE_POSITIVE} always means
 * "rejected with at least the configured confidence": a rejection below the threshold is
 * recorded as {@code UNSURE}, the fail-open semantics of the precision post-filter.
 */
public enum JudgeVerdict {
    TRUE_POSITIVE,
    FALSE_POSITIVE,
    UNSURE;

    public static Optional<JudgeVerdict> parse(String raw) {
        return Arrays.stream(values())
                .filter(verdict -> verdict.name().equalsIgnoreCase(raw))
                .findFirst();
    }
}
