package pro.softcom.aisentinel.application.pii.remediation.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ChangeFindingStatusPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand.StatusChange;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult.RejectedChange;
import pro.softcom.aisentinel.application.pii.remediation.port.in.SelectionStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.SpaceFalsePositivesChanged;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Batch finding lifecycle transitions: false-positive reporting (immediate), manual
 * handling, restoration to {@code PENDING}. Each change is validated against the domain
 * transition rules and rejected individually on violation; the batch never aborts.
 *
 * <p>A finding without a projection row is implicitly {@code PENDING}; its first
 * transition materialises the row with denormalised fields resolved from the latest
 * scan events (encrypted read, no plaintext access).</p>
 *
 * <p>Applied changes that alter the false-positive set of a space are published as
 * {@link SpaceFalsePositivesChanged} events (one per affected scan/space pair) after
 * persistence, so derived read models such as the exported detection report can be
 * refreshed. Publication is fail-open: a publishing failure never rolls back or fails
 * the status change.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ChangeFindingStatusUseCase implements ChangeFindingStatusPort {

    private static final String REASON_UNKNOWN_FINDING = "error.remediation.finding_not_found";
    private static final String REASON_REDACTED_RESERVED = "error.remediation.redacted_status_reserved";
    private static final String REASON_ILLEGAL_TRANSITION = "error.remediation.invalid_status_transition";

    private final RemediationConfigPort remediationConfigPort;
    private final ScanResultQuery scanResultQuery;
    private final FindingRemediationStore findingRemediationStore;
    private final ScanEventFindingResolver findingResolver;
    private final SelectionResolver selectionResolver;
    private final PublishRemediationEventPort publishRemediationEventPort;
    private final Clock clock;

    @Override
    public FindingStatusChangeResult changeStatusesBySelection(SelectionStatusChangeCommand command) {
        requireEnabled();
        ResolvedSelection resolved = selectionResolver.resolve(command.selection());
        List<StatusChange> changes = Stream.concat(
                        resolved.pageFindings().stream(), resolved.attachmentFindings().stream())
                .map(finding -> new StatusChange(finding.findingId(), command.targetStatus()))
                .toList();
        return changeStatuses(new FindingStatusChangeCommand(changes, command.actor()));
    }

    @Override
    public FindingStatusChangeResult changeStatuses(FindingStatusChangeCommand command) {
        requireEnabled();
        BatchContext context = prepareContext(command);
        List<String> applied = new ArrayList<>();
        List<RejectedChange> rejected = new ArrayList<>();
        Map<String, FindingRemediation> toPersist = new LinkedHashMap<>();
        Set<SpaceFalsePositivesChanged> falsePositiveChanges = new LinkedHashSet<>();
        for (StatusChange change : command.changes()) {
            boolean touchesFalsePositives = touchesFalsePositives(change, context);
            Outcome outcome = apply(change, command.actor(), context);
            if (outcome.rejection() != null) {
                rejected.add(outcome.rejection());
            } else {
                context.rows().put(outcome.row().findingId(), outcome.row());
                toPersist.put(outcome.row().findingId(), outcome.row());
                applied.add(outcome.row().findingId());
                if (touchesFalsePositives) {
                    falsePositiveChanges.add(new SpaceFalsePositivesChanged(
                            outcome.row().scanId(), outcome.row().spaceKey()));
                }
            }
        }
        persist(toPersist);
        publishFalsePositiveChanges(falsePositiveChanges);
        return new FindingStatusChangeResult(applied, rejected);
    }

    private void requireEnabled() {
        if (!remediationConfigPort.isRemediationEnabled()) {
            throw new RemediationDisabledException("PII remediation is disabled by configuration");
        }
    }

    private BatchContext prepareContext(FindingStatusChangeCommand command) {
        Set<String> ids = command.changes().stream()
                .map(StatusChange::findingId)
                .collect(Collectors.toSet());
        Map<String, FindingRemediation> rows = loadRows(ids);
        if (rows.keySet().containsAll(ids)) {
            return new BatchContext(rows, Map.of(), null);
        }
        return withResolvedFindings(rows);
    }

    private Map<String, FindingRemediation> loadRows(Set<String> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return findingRemediationStore.findByIds(ids).stream()
                .collect(Collectors.toMap(FindingRemediation::findingId, Function.identity(),
                        (first, second) -> first, HashMap::new));
    }

    private BatchContext withResolvedFindings(Map<String, FindingRemediation> rows) {
        Optional<LastScanMeta> latestScan = scanResultQuery.findLatestScan();
        if (latestScan.isEmpty()) {
            return new BatchContext(rows, Map.of(), null);
        }
        String scanId = latestScan.get().scanId();
        Map<String, EligibleFinding> eligibleById = findingResolver
                .resolve(scanResultQuery.listItemEventsEncrypted(scanId))
                .findings().stream()
                .collect(Collectors.toMap(EligibleFinding::findingId, Function.identity(),
                        (first, second) -> first));
        return new BatchContext(rows, eligibleById, scanId);
    }

    private Outcome apply(StatusChange change, String actor, BatchContext context) {
        if (change.targetStatus() == FindingRemediationStatus.REDACTED) {
            return Outcome.rejected(change.findingId(), REASON_REDACTED_RESERVED);
        }
        FindingRemediation row = context.rows().get(change.findingId());
        if (row != null) {
            return transitionExisting(row, change, actor);
        }
        return transitionNew(change, actor, context);
    }

    private Outcome transitionExisting(FindingRemediation row, StatusChange change, String actor) {
        if (!row.status().canTransitionTo(change.targetStatus())) {
            return Outcome.rejected(change.findingId(), REASON_ILLEGAL_TRANSITION);
        }
        return Outcome.applied(row.toBuilder()
                .status(change.targetStatus())
                .statusReason(null)
                .actor(actor)
                .occurredAt(clock.instant())
                .build());
    }

    private Outcome transitionNew(StatusChange change, String actor, BatchContext context) {
        EligibleFinding finding = context.eligibleById().get(change.findingId());
        if (finding == null) {
            return Outcome.rejected(change.findingId(), REASON_UNKNOWN_FINDING);
        }
        if (!FindingRemediationStatus.PENDING.canTransitionTo(change.targetStatus())) {
            return Outcome.rejected(change.findingId(), REASON_ILLEGAL_TRANSITION);
        }
        return Outcome.applied(newRow(finding, context.scanId(), change.targetStatus(), actor));
    }

    private FindingRemediation newRow(EligibleFinding finding, String scanId,
                                      FindingRemediationStatus target, String actor) {
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
                .status(target)
                .actor(actor)
                .occurredAt(clock.instant())
                .build();
    }

    private void persist(Map<String, FindingRemediation> toPersist) {
        if (!toPersist.isEmpty()) {
            findingRemediationStore.upsertAll(toPersist.values());
        }
    }

    /**
     * A change alters the false-positive set when it enters or leaves {@code FALSE_POSITIVE};
     * evaluated before {@link #apply} so the pre-transition status is still observable.
     */
    private static boolean touchesFalsePositives(StatusChange change, BatchContext context) {
        FindingRemediation currentRow = context.rows().get(change.findingId());
        return change.targetStatus() == FindingRemediationStatus.FALSE_POSITIVE
                || (currentRow != null && currentRow.status() == FindingRemediationStatus.FALSE_POSITIVE);
    }

    private void publishFalsePositiveChanges(Collection<SpaceFalsePositivesChanged> events) {
        for (SpaceFalsePositivesChanged event : events) {
            try {
                publishRemediationEventPort.publishFalsePositivesChanged(event);
            } catch (Exception failure) {
                log.warn("[FP_EVENT] Could not publish false-positive change for scan {} space {}: {} "
                        + "— derived reports may be stale until the next regeneration",
                        event.scanId(), event.spaceKey(), failure.getMessage());
            }
        }
    }

    private record BatchContext(Map<String, FindingRemediation> rows,
                                Map<String, EligibleFinding> eligibleById,
                                String scanId) {
    }

    private record Outcome(FindingRemediation row, RejectedChange rejection) {

        static Outcome applied(FindingRemediation row) {
            return new Outcome(row, null);
        }

        static Outcome rejected(String findingId, String reason) {
            return new Outcome(null, new RejectedChange(findingId, reason));
        }
    }
}
