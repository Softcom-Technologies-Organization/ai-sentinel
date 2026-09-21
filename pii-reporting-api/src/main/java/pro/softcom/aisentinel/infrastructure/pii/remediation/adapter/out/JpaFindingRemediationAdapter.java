package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgement;
import pro.softcom.aisentinel.domain.pii.judge.JudgeVerdict;
import pro.softcom.aisentinel.domain.pii.judge.SpaceJudgementCounts;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.FindingRemediationRepository;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.entity.FindingRemediationEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PostgreSQL-backed implementation of the finding remediation projection store.
 */
@Component
@RequiredArgsConstructor
public class JpaFindingRemediationAdapter implements FindingRemediationStore {

    private final FindingRemediationRepository repository;

    @Override
    public List<FindingRemediation> findByIds(Collection<String> findingIds) {
        if (findingIds == null || findingIds.isEmpty()) {
            return List.of();
        }
        return toDomainList(repository.findAllById(findingIds));
    }

    @Override
    public List<FindingRemediation> findBySpace(String spaceKey, Set<FindingRemediationStatus> statuses) {
        List<FindingRemediationEntity> entities = hasStatusFilter(statuses)
                ? repository.findBySpaceKeyAndStatusIn(spaceKey, names(statuses))
                : repository.findBySpaceKey(spaceKey);
        return toDomainList(entities);
    }

    @Override
    public List<FindingRemediation> findByPage(String spaceKey, String pageId, Set<FindingRemediationStatus> statuses) {
        List<FindingRemediationEntity> entities = hasStatusFilter(statuses)
                ? repository.findBySpaceKeyAndPageIdAndStatusIn(spaceKey, pageId, names(statuses))
                : repository.findBySpaceKeyAndPageId(spaceKey, pageId);
        return toDomainList(entities);
    }

    @Override
    @Transactional
    public void upsertAll(Collection<FindingRemediation> transitions) {
        if (transitions == null || transitions.isEmpty()) {
            return;
        }
        repository.saveAll(transitions.stream().map(JpaFindingRemediationAdapter::toEntity).toList());
    }

    @Override
    public Map<String, FindingRemediationStatus> findStatusesByIds(Collection<String> findingIds) {
        if (findingIds == null || findingIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(findingIds).stream()
                .collect(Collectors.toMap(
                        FindingRemediationEntity::getFindingId,
                        entity -> FindingRemediationStatus.valueOf(entity.getStatus())));
    }

    @Override
    public List<SpaceJudgementCounts> countJudgementsBySpace() {
        return repository.countJudgementsBySpace().stream()
                .map(row -> new SpaceJudgementCounts((String) row[0],
                        ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    private static boolean hasStatusFilter(Set<FindingRemediationStatus> statuses) {
        return statuses != null && !statuses.isEmpty();
    }

    private static Set<String> names(Set<FindingRemediationStatus> statuses) {
        return statuses.stream().map(Enum::name).collect(Collectors.toSet());
    }

    private static List<FindingRemediation> toDomainList(List<FindingRemediationEntity> entities) {
        return entities.stream().map(JpaFindingRemediationAdapter::toDomain).toList();
    }

    private static FindingRemediationEntity toEntity(FindingRemediation row) {
        return FindingRemediationEntity.builder()
                .findingId(row.findingId())
                .scanId(row.scanId())
                .spaceKey(row.spaceKey())
                .pageId(row.pageId())
                .attachmentName(row.attachmentName())
                .piiType(row.piiType())
                .severity(row.severity().name())
                .detector(row.detector())
                .status(row.status().name())
                .statusReason(row.statusReason())
                .actor(row.actor())
                .occurredAt(row.occurredAt())
                .redactionJobId(row.redactionJobId())
                .judgeVerdict(row.judgement() == null ? null : row.judgement().verdict().name())
                .judgeConfidence(row.judgement() == null ? null : row.judgement().confidence())
                .build();
    }

    private static FindingRemediation toDomain(FindingRemediationEntity entity) {
        return FindingRemediation.builder()
                .findingId(entity.getFindingId())
                .scanId(entity.getScanId())
                .spaceKey(entity.getSpaceKey())
                .pageId(entity.getPageId())
                .attachmentName(entity.getAttachmentName())
                .piiType(entity.getPiiType())
                .severity(PersonallyIdentifiableInformationSeverity.valueOf(entity.getSeverity()))
                .detector(entity.getDetector())
                .status(FindingRemediationStatus.valueOf(entity.getStatus()))
                .statusReason(entity.getStatusReason())
                .actor(entity.getActor())
                .occurredAt(entity.getOccurredAt())
                .redactionJobId(entity.getRedactionJobId())
                .judgement(judgementOf(entity))
                .build();
    }

    private static FindingJudgement judgementOf(FindingRemediationEntity entity) {
        if (entity.getJudgeVerdict() == null || entity.getJudgeConfidence() == null) {
            return null;
        }
        return new FindingJudgement(JudgeVerdict.valueOf(entity.getJudgeVerdict()), entity.getJudgeConfidence());
    }
}
