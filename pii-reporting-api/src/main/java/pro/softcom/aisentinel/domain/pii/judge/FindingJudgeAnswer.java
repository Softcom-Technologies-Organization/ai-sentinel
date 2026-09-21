package pro.softcom.aisentinel.domain.pii.judge;

import java.util.Optional;

/**
 * Raw answer of the judge as relayed by the detector service. {@code verdict} may carry the
 * service's {@code UNPARSEABLE} or {@code ERROR} markers, and {@code error} is non-empty when
 * the judge could not be reached; both yield no honoured verdict.
 */
public record FindingJudgeAnswer(String verdict, double confidence, String reason, String error) {

    /**
     * The verdict once the confidence threshold is applied: a rejection below the threshold
     * is kept as {@code UNSURE}; an unreadable or failed answer yields no verdict at all.
     */
    public Optional<JudgeVerdict> verdictHonoring(double confidenceThreshold) {
        if (error != null && !error.isBlank()) {
            return Optional.empty();
        }
        return JudgeVerdict.parse(verdict).map(parsed -> belowThreshold(parsed, confidenceThreshold)
                ? JudgeVerdict.UNSURE
                : parsed);
    }

    private boolean belowThreshold(JudgeVerdict parsed, double confidenceThreshold) {
        return parsed == JudgeVerdict.FALSE_POSITIVE && confidence < confidenceThreshold;
    }
}
