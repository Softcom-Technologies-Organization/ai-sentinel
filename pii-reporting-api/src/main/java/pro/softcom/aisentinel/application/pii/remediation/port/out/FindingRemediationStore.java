package pro.softcom.aisentinel.application.pii.remediation.port.out;

import pro.softcom.aisentinel.domain.pii.judge.SpaceJudgementCounts;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence port for the finding remediation projection. A finding without a stored row is
 * implicitly {@code PENDING}; callers must treat absence accordingly.
 */
public interface FindingRemediationStore {

    List<FindingRemediation> findByIds(Collection<String> findingIds);

    /**
     * Lists remediation rows of a space, optionally filtered by status.
     *
     * @param statuses statuses to keep; a null or empty set means no status filter
     */
    List<FindingRemediation> findBySpace(String spaceKey, Set<FindingRemediationStatus> statuses);

    /**
     * Lists remediation rows of a single page, optionally filtered by status.
     *
     * @param statuses statuses to keep; a null or empty set means no status filter
     */
    List<FindingRemediation> findByPage(String spaceKey, String pageId, Set<FindingRemediationStatus> statuses);

    /**
     * Inserts or replaces transition rows keyed by {@code findingId}.
     */
    void upsertAll(Collection<FindingRemediation> transitions);

    /**
     * Bulk status lookup; finding ids without a stored row are absent from the result
     * (implicitly {@code PENDING}).
     */
    Map<String, FindingRemediationStatus> findStatusesByIds(Collection<String> findingIds);

    /**
     * Per space, how many findings the LLM judge has evaluated and how many of them still await
     * a reviewer as proposed false positives. Spaces never judged are absent.
     */
    List<SpaceJudgementCounts> countJudgementsBySpace();
}
