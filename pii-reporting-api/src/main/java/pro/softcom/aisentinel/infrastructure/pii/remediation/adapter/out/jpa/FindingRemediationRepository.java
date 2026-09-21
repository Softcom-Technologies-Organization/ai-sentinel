package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.entity.FindingRemediationEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface FindingRemediationRepository extends JpaRepository<FindingRemediationEntity, String> {

    List<FindingRemediationEntity> findBySpaceKey(String spaceKey);

    List<FindingRemediationEntity> findBySpaceKeyAndStatusIn(String spaceKey, Collection<String> statuses);

    List<FindingRemediationEntity> findBySpaceKeyAndPageId(String spaceKey, String pageId);

    List<FindingRemediationEntity> findBySpaceKeyAndPageIdAndStatusIn(
            String spaceKey, String pageId, Collection<String> statuses);

    /**
     * Per space: judged rows and, among them, those still parked as proposed false positives.
     * Rows are {@code [spaceKey, judgedCount, proposedCount]}.
     */
    @Query("""
        SELECT e.spaceKey, COUNT(e),
               SUM(CASE WHEN e.status = 'PROPOSED_FALSE_POSITIVE' THEN 1 ELSE 0 END)
        FROM FindingRemediationEntity e
        WHERE e.judgeVerdict IS NOT NULL
        GROUP BY e.spaceKey
        """)
    List<Object[]> countJudgementsBySpace();
}
