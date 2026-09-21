package pro.softcom.aisentinel.application.pii.remediation.service;

import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves a criteria-based {@link RemediationSelection} into the concrete findings of
 * the latest scan of that space. Only {@code PENDING} findings are resolved (enforced by
 * {@link SelectionEvaluator}); attachment findings are excluded from the redactable set
 * and surfaced separately so plans can report them as exclusions.
 *
 * <p>Reads events in encrypted mode only: no plaintext value is decrypted here.</p>
 */
@RequiredArgsConstructor
public class SelectionResolver {

    private final ScanResultQuery scanResultQuery;
    private final ScanCheckpointRepository checkpointRepository;
    private final FindingRemediationStore findingRemediationStore;
    private final ScanEventFindingResolver findingResolver;
    private final SelectionEvaluator selectionEvaluator;

    public ResolvedSelection resolve(RemediationSelection selection) {
        return checkpointRepository.findLatestBySpace(selection.spaceKey())
                .map(scan -> resolveScan(scan.scanId(), selection))
                .orElseGet(ResolvedSelection::empty);
    }

    private ResolvedSelection resolveScan(String scanId, RemediationSelection selection) {
        List<ConfluenceContentScanResult> events = scanResultQuery
                .listItemEventsEncryptedByScanIdAndSpaceKey(scanId, selection.spaceKey()).stream()
                .filter(event -> inScope(event, selection))
                .toList();
        List<EligibleFinding> findings = findingResolver.resolve(events).findings();
        Map<String, FindingRemediationStatus> statuses = statusesOf(findings);
        Map<Boolean, List<EligibleFinding>> selectedByAttachment = findings.stream()
                .filter(finding -> selectionEvaluator.isSelected(finding, statusOf(statuses, finding), selection))
                .collect(Collectors.partitioningBy(finding -> finding.reference().attachmentName() != null));
        return new ResolvedSelection(scanId,
                selectedByAttachment.get(false),
                selectedByAttachment.get(true),
                countFalsePositives(statuses));
    }

    private static boolean inScope(ConfluenceContentScanResult event, RemediationSelection selection) {
        if (selection.pageId() == null) {
            return true;
        }
        if (!selection.pageId().equals(event.pageId())) {
            return false;
        }
        return selection.attachmentName() == null || selection.attachmentName().equals(event.attachmentName());
    }

    private Map<String, FindingRemediationStatus> statusesOf(List<EligibleFinding> findings) {
        if (findings.isEmpty()) {
            return Map.of();
        }
        List<String> ids = findings.stream().map(EligibleFinding::findingId).toList();
        return findingRemediationStore.findStatusesByIds(ids);
    }

    private static FindingRemediationStatus statusOf(Map<String, FindingRemediationStatus> statuses,
                                                     EligibleFinding finding) {
        return statuses.getOrDefault(finding.findingId(), FindingRemediationStatus.PENDING);
    }

    private static long countFalsePositives(Map<String, FindingRemediationStatus> statuses) {
        return statuses.values().stream()
                .filter(status -> status == FindingRemediationStatus.FALSE_POSITIVE)
                .count();
    }

    /**
     * @param pageFindings       redactable findings living in page bodies
     * @param attachmentFindings selected findings living in attachments, not redactable in v1
     * @param falsePositivesReported number of scope findings currently reported as false positives
     */
    public record ResolvedSelection(String scanId,
                                    List<EligibleFinding> pageFindings,
                                    List<EligibleFinding> attachmentFindings,
                                    long falsePositivesReported) {

        public static ResolvedSelection empty() {
            return new ResolvedSelection(null, List.of(), List.of(), 0);
        }

        public List<String> pageFindingIds() {
            return pageFindings.stream().map(EligibleFinding::findingId).toList();
        }
    }
}
