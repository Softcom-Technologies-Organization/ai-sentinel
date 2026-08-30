package pro.softcom.aisentinel.application.pii.remediation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.ObfuscationJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort.PageRedactionResult;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort.ValueRedactionStatus;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort.ValueReplacement;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRedactionOutcome;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJob;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RedactionOutcome;
import pro.softcom.aisentinel.domain.pii.remediation.RedactionToken;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a redaction job page by page: decrypts the page's PII values through the
 * audited {@code REDACTION} access path, hands plaintext/token pairs to the
 * format-agnostic {@link SourcePageRedactionPort} and records one outcome per finding.
 * A failing page never stops the job; the job then ends {@code COMPLETED_WITH_ERRORS}.
 *
 * <p>This runner never touches the page markup and never logs or persists plaintext
 * values. Findings redacted by the port are transitioned to {@code REDACTED}; findings
 * whose value was not found stay implicitly {@code PENDING}.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ObfuscationJobRunner {

    private static final String REASON_PLAINTEXT_UNAVAILABLE = "error.remediation.plaintext_unavailable";
    private static final String REASON_VALUE_NOT_FOUND = "error.remediation.value_not_found";
    private static final String REASON_PAGE_STALE = "error.remediation.page_stale";
    private static final String REASON_PAGE_FAILED = "error.remediation.page_redaction_failed";
    private static final String REASON_ATTACHMENT = "error.remediation.attachment_not_supported";

    private final ScanResultQuery scanResultQuery;
    private final FindingRemediationStore findingRemediationStore;
    private final ObfuscationJobStore obfuscationJobStore;
    private final SourcePageRedactionPort sourcePageRedactionPort;
    private final Clock clock;

    public void run(ObfuscationJob job, ResolvedSelection resolved) {
        try {
            runPages(job, resolved);
        } catch (Exception e) {
            log.error("[PII_REMEDIATION] Job {} failed: {}", job.id(), e.getClass().getSimpleName());
            failJob(job);
        }
    }

    private void runPages(ObfuscationJob job, ResolvedSelection resolved) {
        Map<String, List<EligibleFinding>> findingsByPage = resolved.pageFindings().stream()
                .collect(Collectors.groupingBy(finding -> finding.reference().pageId(),
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, FindingRedactionOutcome> outcomes = new LinkedHashMap<>();
        ObfuscationJob current = job;
        for (Map.Entry<String, List<EligibleFinding>> page : findingsByPage.entrySet()) {
            outcomes.putAll(processPage(job, resolved.scanId(), page.getKey(), page.getValue()));
            current = current.toBuilder()
                    .processed(outcomes.size())
                    .outcomes(outcomes)
                    .updatedAt(clock.instant())
                    .build();
            obfuscationJobStore.update(current);
        }
        obfuscationJobStore.update(current.toBuilder()
                .status(finalStatusOf(outcomes.values()))
                .updatedAt(clock.instant())
                .build());
    }

    private Map<String, FindingRedactionOutcome> processPage(ObfuscationJob job, String scanId,
                                                             String pageId, List<EligibleFinding> findings) {
        Map<String, FindingRedactionOutcome> pageOutcomes = redactPage(scanId, pageId, findings);
        persistRedactedStatuses(job, scanId, findings, pageOutcomes);
        return pageOutcomes;
    }

    private Map<String, FindingRedactionOutcome> redactPage(String scanId, String pageId,
                                                            List<EligibleFinding> findings) {
        try {
            return redactPageValues(scanId, pageId, findings);
        } catch (Exception e) {
            log.error("[PII_REMEDIATION] Redaction of page {} failed: {}",
                    pageId, e.getClass().getSimpleName());
            return outcomesFor(findings, RedactionOutcome.FAILED, REASON_PAGE_FAILED);
        }
    }

    private Map<String, FindingRedactionOutcome> redactPageValues(String scanId, String pageId,
                                                                  List<EligibleFinding> findings) {
        Map<String, FindingRedactionOutcome> outcomes = new LinkedHashMap<>();
        List<EligibleFinding> redactable = excludeAttachmentFindings(findings, outcomes);
        if (redactable.isEmpty()) {
            return outcomes;
        }
        Map<String, String> plaintextByKey = decryptPlaintextValues(scanId, pageId);
        Map<String, List<EligibleFinding>> findingsByPair =
                pairFindingsWithValues(redactable, plaintextByKey, outcomes);
        if (findingsByPair.isEmpty()) {
            return outcomes;
        }
        List<String> pairKeys = List.copyOf(findingsByPair.keySet());
        PageRedactionResult result = sourcePageRedactionPort.redactPage(pageId,
                toReplacements(pairKeys, findingsByPair, plaintextByKey));
        applyPageResult(result, pairKeys, findingsByPair, outcomes);
        return outcomes;
    }

    private static List<EligibleFinding> excludeAttachmentFindings(
            List<EligibleFinding> findings, Map<String, FindingRedactionOutcome> outcomes) {
        List<EligibleFinding> redactable = new ArrayList<>();
        for (EligibleFinding finding : findings) {
            if (finding.reference().attachmentName() != null) {
                outcomes.put(finding.findingId(), outcomeOf(finding,
                        RedactionOutcome.SKIPPED_ATTACHMENT, REASON_ATTACHMENT));
            } else {
                redactable.add(finding);
            }
        }
        return redactable;
    }

    private Map<String, String> decryptPlaintextValues(String scanId, String pageId) {
        Map<String, String> plaintextByKey = new HashMap<>();
        scanResultQuery.listItemEventsDecrypted(scanId, pageId, AccessPurpose.REDACTION).stream()
                .filter(event -> event.attachmentName() == null)
                .flatMap(ObfuscationJobRunner::detectionsOf)
                .filter(ObfuscationJobRunner::carriesUsableValue)
                .forEach(detection -> plaintextByKey.putIfAbsent(
                        pairKeyOf(detection.piiType(), detection.valueFingerprint()),
                        detection.sensitiveValue()));
        return plaintextByKey;
    }

    private static Stream<DetectedPersonallyIdentifiableInformation> detectionsOf(
            ConfluenceContentScanResult event) {
        return event.detectedPIIs() == null ? Stream.empty() : event.detectedPIIs().stream();
    }

    private static boolean carriesUsableValue(DetectedPersonallyIdentifiableInformation detection) {
        return detection.valueFingerprint() != null && !detection.valueFingerprint().isBlank()
                && detection.sensitiveValue() != null;
    }

    private static Map<String, List<EligibleFinding>> pairFindingsWithValues(
            List<EligibleFinding> findings, Map<String, String> plaintextByKey,
            Map<String, FindingRedactionOutcome> outcomes) {
        Map<String, List<EligibleFinding>> findingsByPair = new LinkedHashMap<>();
        for (EligibleFinding finding : findings) {
            String pairKey = pairKeyOf(finding.reference().piiType(), finding.reference().valueFingerprint());
            if (plaintextByKey.containsKey(pairKey)) {
                findingsByPair.computeIfAbsent(pairKey, key -> new ArrayList<>()).add(finding);
            } else {
                outcomes.put(finding.findingId(), outcomeOf(finding,
                        RedactionOutcome.FAILED, REASON_PLAINTEXT_UNAVAILABLE));
            }
        }
        return findingsByPair;
    }

    private static List<ValueReplacement> toReplacements(List<String> pairKeys,
                                                         Map<String, List<EligibleFinding>> findingsByPair,
                                                         Map<String, String> plaintextByKey) {
        return pairKeys.stream()
                .map(pairKey -> new ValueReplacement(plaintextByKey.get(pairKey),
                        RedactionToken.forType(findingsByPair.get(pairKey).getFirst().reference().piiType())))
                .toList();
    }

    private static void applyPageResult(PageRedactionResult result, List<String> pairKeys,
                                        Map<String, List<EligibleFinding>> findingsByPair,
                                        Map<String, FindingRedactionOutcome> outcomes) {
        switch (result.pageStatus()) {
            case STALE -> markAll(findingsByPair, outcomes, RedactionOutcome.SKIPPED_STALE, REASON_PAGE_STALE);
            case FAILED -> markAll(findingsByPair, outcomes, RedactionOutcome.FAILED, REASON_PAGE_FAILED);
            case UPDATED, NO_MATCHES -> markPerValue(result, pairKeys, findingsByPair, outcomes);
        }
    }

    private static void markAll(Map<String, List<EligibleFinding>> findingsByPair,
                                Map<String, FindingRedactionOutcome> outcomes,
                                RedactionOutcome outcome, String reason) {
        findingsByPair.values().stream()
                .flatMap(Collection::stream)
                .forEach(finding -> outcomes.put(finding.findingId(), outcomeOf(finding, outcome, reason)));
    }

    private static void markPerValue(PageRedactionResult result, List<String> pairKeys,
                                     Map<String, List<EligibleFinding>> findingsByPair,
                                     Map<String, FindingRedactionOutcome> outcomes) {
        for (int i = 0; i < pairKeys.size(); i++) {
            boolean redacted = result.valueStatuses().get(i) == ValueRedactionStatus.REDACTED;
            for (EligibleFinding finding : findingsByPair.get(pairKeys.get(i))) {
                outcomes.put(finding.findingId(), redacted
                        ? outcomeOf(finding, RedactionOutcome.REDACTED, null)
                        : outcomeOf(finding, RedactionOutcome.SKIPPED_VALUE_NOT_FOUND, REASON_VALUE_NOT_FOUND));
            }
        }
    }

    private void persistRedactedStatuses(ObfuscationJob job, String scanId,
                                         List<EligibleFinding> findings,
                                         Map<String, FindingRedactionOutcome> pageOutcomes) {
        List<EligibleFinding> redacted = findings.stream()
                .filter(finding -> pageOutcomes.get(finding.findingId()).outcome() == RedactionOutcome.REDACTED)
                .toList();
        if (redacted.isEmpty()) {
            return;
        }
        Map<String, FindingRemediation> existingRows = existingRowsOf(redacted);
        findingRemediationStore.upsertAll(redacted.stream()
                .map(finding -> redactedRow(finding, existingRows.get(finding.findingId()), job, scanId))
                .toList());
    }

    private Map<String, FindingRemediation> existingRowsOf(List<EligibleFinding> findings) {
        List<String> ids = findings.stream().map(EligibleFinding::findingId).toList();
        return findingRemediationStore.findByIds(ids).stream()
                .collect(Collectors.toMap(FindingRemediation::findingId, Function.identity()));
    }

    private FindingRemediation redactedRow(EligibleFinding finding, FindingRemediation existing,
                                           ObfuscationJob job, String scanId) {
        if (existing != null) {
            return existing.toBuilder()
                    .status(FindingRemediationStatus.REDACTED)
                    .statusReason(null)
                    .actor(job.actor())
                    .occurredAt(clock.instant())
                    .redactionJobId(job.id())
                    .build();
        }
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
                .status(FindingRemediationStatus.REDACTED)
                .actor(job.actor())
                .occurredAt(clock.instant())
                .redactionJobId(job.id())
                .build();
    }

    private static ObfuscationJobStatus finalStatusOf(Collection<FindingRedactionOutcome> outcomes) {
        boolean allRedacted = outcomes.stream()
                .allMatch(outcome -> outcome.outcome() == RedactionOutcome.REDACTED);
        return allRedacted ? ObfuscationJobStatus.COMPLETED : ObfuscationJobStatus.COMPLETED_WITH_ERRORS;
    }

    private static Map<String, FindingRedactionOutcome> outcomesFor(List<EligibleFinding> findings,
                                                                    RedactionOutcome outcome, String reason) {
        return findings.stream().collect(Collectors.toMap(EligibleFinding::findingId,
                finding -> outcomeOf(finding, outcome, reason),
                (first, second) -> first, LinkedHashMap::new));
    }

    private static FindingRedactionOutcome outcomeOf(EligibleFinding finding,
                                                     RedactionOutcome outcome, String reason) {
        return new FindingRedactionOutcome(finding.reference().piiType(), outcome, reason);
    }

    private static String pairKeyOf(String piiType, String valueFingerprint) {
        return piiType + "|" + valueFingerprint;
    }

    private void failJob(ObfuscationJob job) {
        try {
            ObfuscationJob latest = obfuscationJobStore.findById(job.id()).orElse(job);
            obfuscationJobStore.update(latest.toBuilder()
                    .status(ObfuscationJobStatus.FAILED)
                    .updatedAt(clock.instant())
                    .build());
        } catch (Exception e) {
            log.error("[PII_REMEDIATION] Could not mark job {} as FAILED: {}",
                    job.id(), e.getClass().getSimpleName());
        }
    }
}
