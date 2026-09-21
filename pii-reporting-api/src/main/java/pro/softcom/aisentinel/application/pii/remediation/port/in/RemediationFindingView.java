package pro.softcom.aisentinel.application.pii.remediation.port.in;

import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.judge.JudgeVerdict;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

/**
 * Read-model row of a single finding. Carries the masked context plus, when the caller is
 * authorized to reveal secrets (gated by {@code pii.reporting.allow-secret-reveal}), the
 * plaintext {@code sensitiveValue} and its surrounding {@code sensitiveContext}; those fields
 * are null when revelation is not allowed. {@code judgeVerdict} and {@code judgeConfidence} are
 * set once the LLM judge has evaluated the finding.
 */
@Builder(toBuilder = true)
public record RemediationFindingView(
        String findingId,
        String piiType,
        PersonallyIdentifiableInformationSeverity severity,
        String detector,
        double confidenceScore,
        String maskedContext,
        String sensitiveValue,
        String sensitiveContext,
        int occurrenceCount,
        String pageId,
        String pageTitle,
        String attachmentName,
        FindingRemediationStatus status,
        boolean selected,
        boolean eligibleForRedaction,
        String ineligibilityReason,
        JudgeVerdict judgeVerdict,
        Double judgeConfidence
) {
}
