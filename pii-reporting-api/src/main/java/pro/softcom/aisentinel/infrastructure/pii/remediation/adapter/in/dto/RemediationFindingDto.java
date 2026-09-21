package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single finding row of the remediation view. {@code sensitiveValue} holds the plaintext
 * value and {@code sensitiveContext} its surrounding line; both are only present when the
 * remediation feature is enabled together with {@code pii.reporting.allow-secret-reveal},
 * and are omitted (null) otherwise so masked-only deployments keep the previous contract.
 * The full page {@code sourceContent} is never exposed here. {@code judgeVerdict} and
 * {@code judgeConfidence} are present once the LLM judge has evaluated the finding.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemediationFindingDto(
        String findingId,
        String piiType,
        String severity,
        String detector,
        double confidenceScore,
        String maskedContext,
        String sensitiveValue,
        String sensitiveContext,
        int occurrenceCount,
        String pageId,
        String pageTitle,
        String attachmentName,
        String status,
        boolean selected,
        boolean eligibleForRedaction,
        String ineligibilityReason,
        String judgeVerdict,
        Double judgeConfidence
) {
}
