package pro.softcom.aisentinel.domain.pii.remediation;

import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgement;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;

import java.time.Instant;

/**
 * Projection row materialising the remediation lifecycle of a finding. The absence of a row
 * means the finding is implicitly {@code PENDING}; a row is only created on the first status
 * transition. Denormalised fields (space, page, type, severity, detector) support SQL
 * aggregation without re-reading scan events. {@code judgement} is the LLM judge's conclusion
 * when the finding has been evaluated, null otherwise; a judged finding the judge kept stays
 * {@code PENDING} and therefore owns a row too.
 */
@Builder(toBuilder = true)
public record FindingRemediation(
        String findingId,
        String scanId,
        String spaceKey,
        String pageId,
        String attachmentName,
        String piiType,
        PersonallyIdentifiableInformationSeverity severity,
        String detector,
        FindingRemediationStatus status,
        String statusReason,
        String actor,
        Instant occurredAt,
        String redactionJobId,
        FindingJudgement judgement
) {

    public FindingRemediation {
        requireNonBlank(findingId, "findingId");
        requireNonBlank(scanId, "scanId");
        requireNonBlank(spaceKey, "spaceKey");
        requireNonBlank(pageId, "pageId");
        requireNonBlank(piiType, "piiType");
        requireNonBlank(detector, "detector");
        requireNonBlank(actor, "actor");
        requireNonNull(severity, "severity");
        requireNonNull(status, "status");
        requireNonNull(occurredAt, "occurredAt");
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
}
