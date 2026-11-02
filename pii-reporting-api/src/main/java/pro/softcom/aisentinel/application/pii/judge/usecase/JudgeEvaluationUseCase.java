package pro.softcom.aisentinel.application.pii.judge.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeEvaluationPort;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeLaunchCommand;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgePlan;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeScope;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.judge.service.JudgeJobRunner;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.domain.pii.judge.JudgeScanInProgressException;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Previews and launches judge runs. A launch resolves each targeted space into its pending
 * findings, creates one {@code RUNNING} job per space that has some, then hands the jobs to a
 * single background task that runs them one after the other so the model endpoint is never
 * flooded by several spaces at once. Judging is part of the remediation feature and follows its
 * feature flag.
 */
@RequiredArgsConstructor
@Slf4j
public class JudgeEvaluationUseCase implements JudgeEvaluationPort {

    private final RemediationConfigPort remediationConfigPort;
    private final SelectionResolver selectionResolver;
    private final JudgeJobStore judgeJobStore;
    private final PiiDetectionConfigRepository detectionConfigRepository;
    private final JudgeJobRunner jobRunner;
    private final Executor jobExecutor;
    private final ScanCheckpointRepository checkpointRepository;
    private final ScanResultQuery scanResultQuery;
    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestrator;
    private final Clock clock;

    @Override
    public JudgePlan plan(JudgeScope scope) {
        requireEnabled();
        Map<String, Integer> bySpace = new LinkedHashMap<>();
        for (RemediationSelection selection : scope.selections()) {
            bySpace.put(selection.spaceKey(), findingsOf(selectionResolver.resolve(selection)).size());
        }
        return new JudgePlan(bySpace.values().stream().mapToInt(Integer::intValue).sum(), bySpace);
    }

    @Override
    public List<String> launch(JudgeLaunchCommand command) {
        requireEnabled();
        requireNoUnfinishedScan();
        List<PreparedJob> prepared = prepareJobs(command);
        if (prepared.isEmpty()) {
            throw new IllegalArgumentException("The scope resolves to no pending finding");
        }
        prepared.forEach(job -> judgeJobStore.create(job.job()));
        jobExecutor.execute(() -> prepared.forEach(job -> jobRunner.run(job.job(), job.scanId(), job.findingIds())));
        log.info("[LLM_JUDGE] {} job(s) launched by actor={} with model={}",
                prepared.size(), command.actor(), prepared.getFirst().job().model());
        return prepared.stream().map(job -> job.job().id()).toList();
    }

    private void requireEnabled() {
        if (!remediationConfigPort.isRemediationEnabled()) {
            throw new RemediationDisabledException("PII remediation is disabled by configuration");
        }
    }

    private void requireNoUnfinishedScan() {
        if (scanExecutionOrchestrator.hasActiveScan()) {
            throw new JudgeScanInProgressException();
        }
        String currentScanId = scanResultQuery.findLatestScan().map(LastScanMeta::scanId).orElse(null);
        List<ScanStatus> statuses = checkpointRepository.findAllLatestCheckpoints().stream()
                .filter(checkpoint -> currentScanId == null || currentScanId.equals(checkpoint.scanId()))
                .map(ScanCheckpoint::scanStatus)
                .toList();
        // A paused scan prompts nothing, so the judge is free to use the model endpoint. Its queued
        // spaces still read NOT_STARTED, which would otherwise lock the judge out for the whole
        // pause — and an outage pauses scans on its own.
        if (statuses.contains(ScanStatus.PAUSED)) {
            return;
        }
        boolean unfinished = statuses.stream()
                .anyMatch(status -> status == ScanStatus.RUNNING || status == ScanStatus.NOT_STARTED);
        if (unfinished) {
            throw new JudgeScanInProgressException();
        }
    }

    private List<PreparedJob> prepareJobs(JudgeLaunchCommand command) {
        String model = resolveModel(command.model());
        List<PreparedJob> prepared = new ArrayList<>();
        for (RemediationSelection selection : command.scope().selections()) {
            requireNoActiveJob(selection.spaceKey());
            ResolvedSelection resolved = selectionResolver.resolve(selection);
            List<EligibleFinding> findings = findingsOf(resolved);
            if (!findings.isEmpty()) {
                prepared.add(new PreparedJob(newJob(command, model, selection.spaceKey(), findings.size()),
                        resolved.scanId(), idsOf(findings)));
            }
        }
        return prepared;
    }

    private String resolveModel(String requested) {
        if (isSet(requested)) {
            return requested;
        }
        return detectionConfigRepository.findConfig().lmStudioJudgeModel();
    }

    private static boolean isSet(String model) {
        return model != null && !model.isBlank();
    }

    private void requireNoActiveJob(String spaceKey) {
        if (judgeJobStore.findActiveBySpace(spaceKey).isPresent()) {
            throw new JudgeJobAlreadyRunningException(spaceKey);
        }
    }

    private static List<EligibleFinding> findingsOf(ResolvedSelection resolved) {
        return Stream.concat(resolved.pageFindings().stream(), resolved.attachmentFindings().stream()).toList();
    }

    private static Set<String> idsOf(List<EligibleFinding> findings) {
        return findings.stream().map(EligibleFinding::findingId).collect(Collectors.toSet());
    }

    private JudgeJob newJob(JudgeLaunchCommand command, String model, String spaceKey, int total) {
        Instant now = clock.instant();
        return JudgeJob.builder()
                .id(UUID.randomUUID().toString())
                .spaceKey(spaceKey)
                .status(JudgeJobStatus.RUNNING)
                .model(model)
                .applyMode(command.applyMode())
                .confidenceThreshold(command.confidenceThreshold())
                .total(total)
                .actor(command.actor())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private record PreparedJob(JudgeJob job, String scanId, Set<String> findingIds) {
    }
}
