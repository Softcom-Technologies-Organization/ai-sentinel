package pro.softcom.aisentinel.application.pii.remediation.usecase;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.remediation.port.in.QueryRemediationFindingsPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup.MasterState;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingView;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.GroupBy;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.StatusFilter;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationTotals;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver.Resolution;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionEvaluator;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgement;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Remediation read model: resolves the latest scan events of a scope into eligible
 * findings, joins the remediation projection, applies the view filters and computes
 * every displayed aggregate (groups, tri-state master checkboxes, selected counts,
 * pagination, totals) server-side.
 *
 * <p>Reads events in decrypted mode: the remediation review is gated by
 * {@code pii.reporting.allow-secret-reveal}, so the reviewer sees plaintext values to tell
 * genuine hits from false positives before redacting. Each space view is audited for
 * nLPD compliance.</p>
 */
@RequiredArgsConstructor
public class QueryRemediationFindingsUseCase implements QueryRemediationFindingsPort {

    private static final String ATTACHMENT_INELIGIBILITY_REASON = "ATTACHMENT_REDACTION_UNSUPPORTED";

    private final RemediationConfigPort remediationConfigPort;
    private final ScanResultQuery scanResultQuery;
    private final ScanCheckpointRepository checkpointRepository;
    private final FindingRemediationStore findingRemediationStore;
    private final ScanEventFindingResolver findingResolver;
    private final SelectionEvaluator selectionEvaluator;

    @Override
    public boolean isRemediationEnabled() {
        return remediationConfigPort.isRemediationEnabled();
    }

    @Override
    public RemediationFindingsResult search(RemediationFindingsQuery query) {
        requireEnabled();
        return checkpointRepository.findLatestBySpace(query.spaceKey())
                .map(scan -> searchScan(scan.scanId(), query))
                .orElseGet(() -> emptyResult(query));
    }

    private void requireEnabled() {
        if (!remediationConfigPort.isRemediationEnabled()) {
            throw new RemediationDisabledException("PII remediation is disabled by configuration");
        }
    }

    private RemediationFindingsResult searchScan(String scanId, RemediationFindingsQuery query) {
        List<ConfluenceContentScanResult> scopedEvents = scanResultQuery
                .listItemEventsDecryptedByScanIdAndSpaceKey(scanId, query.spaceKey(), AccessPurpose.USER_DISPLAY)
                .stream()
                .filter(event -> inScope(event, query))
                .toList();
        Resolution resolution = findingResolver.resolve(scopedEvents);
        List<StatusedFinding> visible = visibleFindings(resolution.findings(), query);
        List<StatusedFinding> filtered = visible.stream()
                .filter(finding -> matchesStatusFilter(finding.status(), query.statusFilter()))
                .sorted(orderingFor(query.groupBy()))
                .toList();
        long totalGroups = filtered.stream()
                .map(finding -> groupKeyOf(finding, query.groupBy()))
                .distinct()
                .count();
        return RemediationFindingsResult.builder()
                .groups(buildGroups(filtered, query))
                .totals(totalsOf(visible))
                .page(query.page())
                .pageSize(query.pageSize())
                .totalElements(filtered.size())
                .totalGroups(totalGroups)
                .nonEligibleLegacyCount(resolution.nonEligibleLegacyCount())
                .build();
    }

    private static boolean inScope(ConfluenceContentScanResult event, RemediationFindingsQuery query) {
        if (query.pageId() == null) {
            return true;
        }
        if (!query.pageId().equals(event.pageId())) {
            return false;
        }
        return query.attachmentName() == null || query.attachmentName().equals(event.attachmentName());
    }

    private List<StatusedFinding> visibleFindings(List<EligibleFinding> findings, RemediationFindingsQuery query) {
        Map<String, FindingRemediation> rows = rowsOf(findings);
        return findings.stream()
                .map(finding -> toStatused(finding, rows.get(finding.findingId()), query.selection()))
                .filter(finding -> matchesSearchText(finding.finding(), query.searchText()))
                .filter(finding -> matchesItemFilter(finding.finding().reference(), query.itemFilter()))
                .toList();
    }

    private Map<String, FindingRemediation> rowsOf(List<EligibleFinding> findings) {
        if (findings.isEmpty()) {
            return Map.of();
        }
        List<String> ids = findings.stream().map(EligibleFinding::findingId).toList();
        return findingRemediationStore.findByIds(ids).stream()
                .collect(Collectors.toMap(FindingRemediation::findingId, Function.identity(),
                        (first, second) -> first));
    }

    private StatusedFinding toStatused(EligibleFinding finding, FindingRemediation row,
                                       RemediationSelection selection) {
        FindingRemediationStatus status = row == null ? FindingRemediationStatus.PENDING : row.status();
        FindingJudgement judgement = row == null ? null : row.judgement();
        return new StatusedFinding(finding, status, selectionEvaluator.isSelected(finding, status, selection),
                judgement);
    }

    private static boolean matchesSearchText(EligibleFinding finding, String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return true;
        }
        String needle = searchText.toLowerCase(Locale.ROOT);
        return Stream.of(finding.maskedContext(), finding.sensitiveValue(), finding.pageTitle(),
                        finding.piiTypeLabel(), finding.reference().piiType(),
                        finding.reference().attachmentName())
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean matchesItemFilter(FindingReference reference, String itemFilter) {
        if (itemFilter == null || itemFilter.isBlank()) {
            return true;
        }
        return itemFilter.equals(reference.pageId()) || itemFilter.equals(reference.attachmentName());
    }

    private static boolean matchesStatusFilter(FindingRemediationStatus status, StatusFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case PENDING -> status == FindingRemediationStatus.PENDING;
            case HANDLED -> status == FindingRemediationStatus.REDACTED
                    || status == FindingRemediationStatus.MANUALLY_HANDLED;
            case FALSE_POSITIVE -> status == FindingRemediationStatus.FALSE_POSITIVE
                    || status == FindingRemediationStatus.PROPOSED_FALSE_POSITIVE;
        };
    }

    private static RemediationTotals totalsOf(List<StatusedFinding> visible) {
        long pending = countByStatus(visible, FindingRemediationStatus.PENDING);
        long handled = countByStatus(visible, FindingRemediationStatus.REDACTED)
                + countByStatus(visible, FindingRemediationStatus.MANUALLY_HANDLED);
        long falsePositive = countByStatus(visible, FindingRemediationStatus.FALSE_POSITIVE);
        long proposed = countByStatus(visible, FindingRemediationStatus.PROPOSED_FALSE_POSITIVE);
        return new RemediationTotals(pending, handled, falsePositive, proposed, visible.size());
    }

    private static long countByStatus(List<StatusedFinding> findings, FindingRemediationStatus status) {
        return findings.stream().filter(finding -> finding.status() == status).count();
    }

    private static Comparator<StatusedFinding> orderingFor(GroupBy groupBy) {
        return groupComparator(groupBy)
                .thenComparing(finding -> finding.finding().pageTitle(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(finding -> finding.finding().reference().attachmentName(),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(finding -> finding.finding().findingId());
    }

    private static Comparator<StatusedFinding> groupComparator(GroupBy groupBy) {
        if (groupBy == GroupBy.SEVERITY) {
            return Comparator.comparing(finding -> finding.finding().reference().severity());
        }
        return Comparator.comparing(finding -> finding.finding().reference().piiType());
    }

    private static List<RemediationFindingGroup> buildGroups(List<StatusedFinding> ordered,
                                                             RemediationFindingsQuery query) {
        List<List<StatusedFinding>> byKey = new ArrayList<>(ordered.stream()
                .collect(Collectors.groupingBy(finding -> groupKeyOf(finding, query.groupBy()),
                        LinkedHashMap::new, Collectors.toList()))
                .values());
        int from = Math.clamp((long) query.page() * query.pageSize(), 0, byKey.size());
        int to = Math.clamp((long) (query.page() + 1) * query.pageSize(), 0, byKey.size());
        return byKey.subList(from, to).stream()
                .map(members -> toGroup(members, query.groupBy()))
                .toList();
    }

    private static RemediationFindingGroup toGroup(List<StatusedFinding> members, GroupBy groupBy) {
        long selectedCount = members.stream().filter(StatusedFinding::selected).count();
        long pendingCount = countByStatus(members, FindingRemediationStatus.PENDING);
        long occurrenceCount = members.stream().mapToLong(finding -> finding.finding().occurrenceCount()).sum();
        StatusedFinding first = members.getFirst();
        return RemediationFindingGroup.builder()
                .key(groupKeyOf(first, groupBy))
                .label(labelOf(first.finding(), groupBy))
                .severity(first.finding().reference().severity())
                .total(members.size())
                .occurrenceCount(occurrenceCount)
                .selectedCount(selectedCount)
                .masterState(masterStateOf(pendingCount, selectedCount))
                .findings(members.stream().map(QueryRemediationFindingsUseCase::toView).toList())
                .build();
    }

    private static String groupKeyOf(StatusedFinding finding, GroupBy groupBy) {
        if (groupBy == GroupBy.SEVERITY) {
            return finding.finding().reference().severity().name();
        }
        return finding.finding().reference().piiType();
    }

    private static String labelOf(EligibleFinding finding, GroupBy groupBy) {
        if (groupBy == GroupBy.SEVERITY) {
            return finding.reference().severity().name();
        }
        String label = finding.piiTypeLabel();
        return label == null || label.isBlank() ? finding.reference().piiType() : label;
    }

    private static MasterState masterStateOf(long pendingCount, long selectedCount) {
        if (pendingCount == 0 || selectedCount == 0) {
            return MasterState.NONE;
        }
        return selectedCount == pendingCount ? MasterState.ALL : MasterState.PARTIAL;
    }

    private static RemediationFindingView toView(StatusedFinding statused) {
        EligibleFinding finding = statused.finding();
        FindingReference reference = finding.reference();
        boolean onAttachment = reference.attachmentName() != null;
        return RemediationFindingView.builder()
                .findingId(finding.findingId())
                .piiType(reference.piiType())
                .severity(reference.severity())
                .detector(reference.detector())
                .confidenceScore(finding.confidence())
                .maskedContext(finding.maskedContext())
                .sensitiveValue(finding.sensitiveValue())
                .sensitiveContext(finding.sensitiveContext())
                .occurrenceCount(finding.occurrenceCount())
                .pageId(reference.pageId())
                .pageTitle(finding.pageTitle())
                .attachmentName(reference.attachmentName())
                .status(statused.status())
                .selected(statused.selected())
                .eligibleForRedaction(!onAttachment)
                .ineligibilityReason(onAttachment ? ATTACHMENT_INELIGIBILITY_REASON : null)
                .judgeVerdict(statused.judgement() == null ? null : statused.judgement().verdict())
                .judgeConfidence(statused.judgement() == null ? null : statused.judgement().confidence())
                .build();
    }

    private static RemediationFindingsResult emptyResult(RemediationFindingsQuery query) {
        return RemediationFindingsResult.builder()
                .groups(List.of())
                .totals(RemediationTotals.empty())
                .page(query.page())
                .pageSize(query.pageSize())
                .totalElements(0)
                .totalGroups(0)
                .nonEligibleLegacyCount(0)
                .build();
    }

    private record StatusedFinding(EligibleFinding finding, FindingRemediationStatus status, boolean selected,
                                   FindingJudgement judgement) {
    }
}
