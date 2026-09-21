package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto;

/**
 * Lifecycle breakdown of the findings in scope, for the view header counters.
 */
public record RemediationTotalsDto(long pending, long handled, long falsePositive,
                                   long proposedFalsePositive, long total) {
}
