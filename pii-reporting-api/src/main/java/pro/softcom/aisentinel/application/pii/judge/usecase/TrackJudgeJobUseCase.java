package pro.softcom.aisentinel.application.pii.judge.usecase;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeSpaceSummary;
import pro.softcom.aisentinel.application.pii.judge.port.in.TrackJudgeJobPort;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobNotFoundException;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.judge.SpaceJudgementCounts;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Polling read model of judge jobs, their interruption, and the per-space summary the dashboard
 * shows next to each space's scan status.
 */
@RequiredArgsConstructor
public class TrackJudgeJobUseCase implements TrackJudgeJobPort {

    private final RemediationConfigPort remediationConfigPort;
    private final JudgeJobStore judgeJobStore;
    private final FindingRemediationStore findingRemediationStore;
    private final Clock clock;

    @Override
    public Optional<JudgeJob> findJob(String jobId) {
        requireEnabled();
        return judgeJobStore.findById(jobId);
    }

    @Override
    public void cancel(String jobId) {
        requireEnabled();
        JudgeJob job = judgeJobStore.findById(jobId).orElseThrow(() -> new JudgeJobNotFoundException(jobId));
        if (job.status() == JudgeJobStatus.RUNNING) {
            judgeJobStore.update(job.toBuilder().status(JudgeJobStatus.INTERRUPTED).updatedAt(clock.instant()).build());
        }
    }

    @Override
    public List<JudgeSpaceSummary> spaceSummaries() {
        requireEnabled();
        Map<String, JudgeJob> activeBySpace = judgeJobStore.findAllActive().stream()
                .collect(Collectors.toMap(JudgeJob::spaceKey, Function.identity(), (first, second) -> first));
        Map<String, SpaceJudgementCounts> countsBySpace = findingRemediationStore.countJudgementsBySpace().stream()
                .collect(Collectors.toMap(SpaceJudgementCounts::spaceKey, Function.identity()));
        Set<String> spaceKeys = new LinkedHashSet<>(countsBySpace.keySet());
        spaceKeys.addAll(activeBySpace.keySet());
        return spaceKeys.stream()
                .map(spaceKey -> summaryOf(spaceKey, countsBySpace.get(spaceKey), activeBySpace.get(spaceKey)))
                .toList();
    }

    private static JudgeSpaceSummary summaryOf(String spaceKey, SpaceJudgementCounts counts, JudgeJob activeJob) {
        long judged = counts == null ? 0 : counts.judged();
        long proposed = counts == null ? 0 : counts.proposedFalsePositives();
        return new JudgeSpaceSummary(spaceKey, judged, proposed, activeJob);
    }

    private void requireEnabled() {
        if (!remediationConfigPort.isRemediationEnabled()) {
            throw new RemediationDisabledException("PII remediation is disabled by configuration");
        }
    }
}
