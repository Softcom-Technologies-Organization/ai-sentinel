package pro.softcom.aisentinel.application.pii.judge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeAnswer;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeRequest;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgement;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.judge.JudgeVerdict;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.SpaceFalsePositivesChanged;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runs one judge job: decrypts the space's findings once through the audited
 * {@code LLM_JUDGE} access path, asks the judge about each finding in turn and records the
 * honoured verdict on the finding row. A finding the judge could not assess is counted as an
 * error and skipped; a detector-service outage fails the job. Progress is persisted after every
 * verdict, and an interruption requested meanwhile is honoured before the next call.
 *
 * <p>Never logs or persists plaintext values.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class JudgeJobRunner {

    private final ScanResultQuery scanResultQuery;
    private final ScanEventFindingResolver findingResolver;
    private final FindingRemediationStore findingRemediationStore;
    private final JudgeJobStore judgeJobStore;
    private final PiiDetectorClient piiDetectorClient;
    private final PiiDetectionConfigRepository detectionConfigRepository;
    private final PublishRemediationEventPort publishRemediationEventPort;
    private final Clock clock;
    private final JudgeEndpoint judgeEndpoint;

    public void run(JudgeJob job, String scanId, Set<String> findingIds) {
        try {
            Run run = new Run(scanId, endpointOf(job));
            JudgeJob finished = judgeAll(job, run, decryptedFindings(job.spaceKey(), scanId, findingIds));
            judgeJobStore.update(finished);
            publishIfAutoApplied(finished, scanId);
        } catch (Exception e) {
            log.error("[LLM_JUDGE] Job {} failed: {}", job.id(), e.getClass().getSimpleName());
            failJob(job);
        }
    }

    private Endpoint endpointOf(JudgeJob job) {
        PiiDetectionConfig config = detectionConfigRepository.findConfig();
        JudgeEndpoint endpoint = judgeEndpoint.resolve(config.lmStudioHost(), config.lmStudioPort());
        return new Endpoint(job.model(), endpoint.host(), endpoint.port());
    }

    private List<EligibleFinding> decryptedFindings(String spaceKey, String scanId, Set<String> findingIds) {
        return findingResolver.resolve(scanResultQuery
                        .listItemEventsDecryptedByScanIdAndSpaceKey(scanId, spaceKey, AccessPurpose.LLM_JUDGE))
                .findings().stream()
                .filter(finding -> findingIds.contains(finding.findingId()) && finding.sensitiveValue() != null)
                .toList();
    }

    private JudgeJob judgeAll(JudgeJob job, Run run, List<EligibleFinding> findings) {
        JudgeJob current = job;
        for (EligibleFinding finding : findings) {
            current = judgeOne(current, finding, run);
            if (interruptionRequested(current)) {
                return current.toBuilder().status(JudgeJobStatus.INTERRUPTED).build();
            }
            judgeJobStore.update(current);
        }
        return current.toBuilder().status(JudgeJobStatus.COMPLETED).updatedAt(clock.instant()).build();
    }

    private boolean interruptionRequested(JudgeJob job) {
        return judgeJobStore.findById(job.id())
                .map(stored -> stored.status() == JudgeJobStatus.INTERRUPTED)
                .orElse(false);
    }

    private JudgeJob judgeOne(JudgeJob job, EligibleFinding finding, Run run) {
        FindingJudgeAnswer answer = piiDetectorClient.judgeFinding(requestFor(finding, run.endpoint()));
        Optional<JudgeVerdict> verdict = answer.verdictHonoring(job.confidenceThreshold());
        JudgeJob.JudgeJobBuilder next = job.toBuilder().processed(job.processed() + 1).updatedAt(clock.instant());
        if (verdict.isEmpty()) {
            log.warn("[LLM_JUDGE] Job {}: no verdict for finding {} ({} / {})",
                    job.id(), finding.findingId(), answer.verdict(), answer.error());
            return next.errors(job.errors() + 1).build();
        }
        FindingJudgement judgement = new FindingJudgement(verdict.get(), answer.confidence());
        boolean recorded = recordVerdict(job, finding, judgement, run.scanId());
        return next.falsePositives(job.falsePositives() + (recorded && judgement.rejects() ? 1 : 0)).build();
    }

    private static FindingJudgeRequest requestFor(EligibleFinding finding, Endpoint endpoint) {
        return new FindingJudgeRequest(
                finding.reference().piiType(),
                finding.piiTypeLabel(),
                finding.sensitiveValue(),
                JudgeContextWindow.around(finding.sensitiveContext(), finding.sensitiveValue()),
                endpoint.model(),
                endpoint.host(),
                endpoint.port());
    }

    /**
     * Writes the verdict unless the finding left {@code PENDING} meanwhile (a redaction or a
     * reviewer got there first).
     */
    private boolean recordVerdict(JudgeJob job, EligibleFinding finding, FindingJudgement judgement, String scanId) {
        FindingRemediation existing = findingRemediationStore.findByIds(List.of(finding.findingId())).stream()
                .findFirst()
                .orElse(null);
        // ponytail: read-then-write leaves a millisecond race with a redaction job on the same
        // finding; switch to a conditional update if it ever bites.
        if (existing != null && existing.status() != FindingRemediationStatus.PENDING) {
            return false;
        }
        FindingRemediation row = existing == null
                ? newRow(finding, job, scanId, judgement)
                : existing.toBuilder()
                        .status(statusFor(job.applyMode(), judgement))
                        .judgement(judgement)
                        .actor(job.actor())
                        .occurredAt(clock.instant())
                        .build();
        findingRemediationStore.upsertAll(List.of(row));
        return true;
    }

    private static FindingRemediationStatus statusFor(JudgeApplyMode applyMode, FindingJudgement judgement) {
        if (!judgement.rejects()) {
            return FindingRemediationStatus.PENDING;
        }
        return applyMode == JudgeApplyMode.AUTO_APPLY
                ? FindingRemediationStatus.FALSE_POSITIVE
                : FindingRemediationStatus.PROPOSED_FALSE_POSITIVE;
    }

    private FindingRemediation newRow(EligibleFinding finding, JudgeJob job, String scanId,
                                      FindingJudgement judgement) {
        FindingReference reference = finding.reference();
        return FindingRemediation.builder()
                .findingId(finding.findingId())
                .scanId(scanId)
                .spaceKey(reference.spaceKey())
                .pageId(reference.pageId())
                .attachmentName(reference.attachmentName())
                .piiType(reference.piiType())
                .severity(reference.severity())
                .detector(reference.detector())
                .status(statusFor(job.applyMode(), judgement))
                .actor(job.actor())
                .occurredAt(clock.instant())
                .judgement(judgement)
                .build();
    }

    private void publishIfAutoApplied(JudgeJob job, String scanId) {
        if (job.applyMode() != JudgeApplyMode.AUTO_APPLY || job.falsePositives() == 0) {
            return;
        }
        try {
            publishRemediationEventPort.publishFalsePositivesChanged(
                    new SpaceFalsePositivesChanged(scanId, job.spaceKey()));
        } catch (Exception failure) {
            log.warn("[LLM_JUDGE] Could not publish false-positive change for space {}: {}",
                    job.spaceKey(), failure.getMessage());
        }
    }

    private void failJob(JudgeJob job) {
        try {
            JudgeJob latest = judgeJobStore.findById(job.id()).orElse(job);
            judgeJobStore.update(latest.toBuilder().status(JudgeJobStatus.FAILED).updatedAt(clock.instant()).build());
        } catch (Exception e) {
            log.error("[LLM_JUDGE] Could not mark job {} as FAILED: {}", job.id(), e.getClass().getSimpleName());
        }
    }

    private record Endpoint(String model, String host, Integer port) {
    }

    private record Run(String scanId, Endpoint endpoint) {
    }
}
