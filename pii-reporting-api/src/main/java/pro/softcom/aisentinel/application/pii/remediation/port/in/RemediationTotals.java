package pro.softcom.aisentinel.application.pii.remediation.port.in;

/**
 * Lifecycle breakdown of the findings in scope: {@code handled} covers both
 * {@code REDACTED} and {@code MANUALLY_HANDLED}; {@code proposedFalsePositive} counts the
 * LLM judge rejections still awaiting a reviewer, apart from confirmed false positives.
 */
public record RemediationTotals(long pending, long handled, long falsePositive,
                                long proposedFalsePositive, long total) {

    public static RemediationTotals empty() {
        return new RemediationTotals(0, 0, 0, 0, 0);
    }
}
