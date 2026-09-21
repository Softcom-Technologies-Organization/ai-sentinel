package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingView;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.GroupBy;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.StatusFilter;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.SelectionStatusChangeCommand;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationFindingDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationGroupDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSelectionDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationTotalsDto;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps remediation REST DTOs to application port models and back. Invalid enum-like
 * inputs raise {@link IllegalArgumentException}, mapped to 400 by the global handler.
 */
@Component
public class RemediationDtoMapper {

    private static final int DEFAULT_PAGE_SIZE = 50;

    public RemediationFindingsQuery toQuery(RemediationSearchRequestDto request) {
        RemediationScopeDto scope = requireScope(request.scope());
        return RemediationFindingsQuery.builder()
                .spaceKey(scope.spaceKey())
                .pageId(scope.pageId())
                .attachmentName(scope.attachmentName())
                .groupBy(parseGroupBy(request.groupBy()))
                .statusFilter(parseStatusFilter(request.statusFilter()))
                .searchText(request.searchText())
                .itemFilter(request.itemFilter())
                .page(request.page() == null ? 0 : request.page())
                .pageSize(request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize())
                .selection(toSelection(scope, request.selection()))
                .build();
    }

    public FindingStatusChangeCommand toCommand(FindingStatusChangeRequestDto request, String actor) {
        if (request.changes() == null) {
            throw new IllegalArgumentException("changes is required");
        }
        List<FindingStatusChangeCommand.StatusChange> changes = request.changes().stream()
                .map(this::toStatusChange)
                .toList();
        return new FindingStatusChangeCommand(changes, actor);
    }

    public SelectionStatusChangeCommand toSelectionCommand(RemediationSelection selection,
                                                           String targetStatus, String actor) {
        if (targetStatus == null || targetStatus.isBlank()) {
            throw new IllegalArgumentException("targetStatus is required");
        }
        return new SelectionStatusChangeCommand(selection,
                FindingRemediationStatus.valueOf(targetStatus.toUpperCase(Locale.ROOT)), actor);
    }

    public RemediationSearchResponseDto toDto(RemediationFindingsResult result) {
        return new RemediationSearchResponseDto(
                result.groups().stream().map(this::toGroupDto).toList(),
                new RemediationTotalsDto(result.totals().pending(), result.totals().handled(),
                        result.totals().falsePositive(), result.totals().proposedFalsePositive(),
                        result.totals().total()),
                result.page(),
                result.pageSize(),
                result.totalElements(),
                result.totalGroups(),
                result.nonEligibleLegacyCount());
    }

    public FindingStatusChangeResponseDto toDto(FindingStatusChangeResult result) {
        List<FindingStatusChangeResponseDto.RejectedChangeDto> rejected = result.rejected().stream()
                .map(change -> new FindingStatusChangeResponseDto.RejectedChangeDto(
                        change.findingId(), change.reason()))
                .toList();
        return new FindingStatusChangeResponseDto(result.applied(), rejected);
    }

    private static RemediationScopeDto requireScope(RemediationScopeDto scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        return scope;
    }

    private static GroupBy parseGroupBy(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("groupBy is required");
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "type" -> GroupBy.TYPE;
            case "severity" -> GroupBy.SEVERITY;
            default -> throw new IllegalArgumentException("unsupported groupBy: " + raw);
        };
    }

    private static StatusFilter parseStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return StatusFilter.ALL;
        }
        return StatusFilter.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    private static RemediationSelection toSelection(RemediationScopeDto scope, RemediationSelectionDto selection) {
        return RemediationSelection.builder()
                .spaceKey(scope.spaceKey())
                .pageId(scope.pageId())
                .attachmentName(scope.attachmentName())
                .piiTypes(selection == null ? List.of() : selection.piiTypes())
                .severities(selection == null ? List.of() : parseSeverities(selection.severities()))
                .excludedFindingIds(selection == null || selection.excludedFindingIds() == null
                        ? Set.of() : Set.copyOf(selection.excludedFindingIds()))
                .includedFindingIds(selection == null || selection.includedFindingIds() == null
                        ? Set.of() : Set.copyOf(selection.includedFindingIds()))
                .build();
    }

    private static List<PersonallyIdentifiableInformationSeverity> parseSeverities(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(severity -> PersonallyIdentifiableInformationSeverity.valueOf(
                        severity.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private FindingStatusChangeCommand.StatusChange toStatusChange(
            FindingStatusChangeRequestDto.FindingStatusChangeDto change) {
        if (change == null || change.targetStatus() == null) {
            throw new IllegalArgumentException("each change requires findingId and targetStatus");
        }
        return new FindingStatusChangeCommand.StatusChange(change.findingId(),
                FindingRemediationStatus.valueOf(change.targetStatus().toUpperCase(Locale.ROOT)));
    }

    private RemediationGroupDto toGroupDto(RemediationFindingGroup group) {
        return new RemediationGroupDto(
                group.key(),
                group.label(),
                group.severity() == null ? null : group.severity().name(),
                group.total(),
                group.occurrenceCount(),
                group.selectedCount(),
                group.masterState().name().toLowerCase(Locale.ROOT),
                group.findings().stream().map(this::toFindingDto).toList());
    }

    private RemediationFindingDto toFindingDto(RemediationFindingView view) {
        return new RemediationFindingDto(
                view.findingId(),
                view.piiType(),
                view.severity().name(),
                view.detector(),
                view.confidenceScore(),
                view.maskedContext(),
                view.sensitiveValue(),
                view.sensitiveContext(),
                view.occurrenceCount(),
                view.pageId(),
                view.pageTitle(),
                view.attachmentName(),
                view.status().name(),
                view.selected(),
                view.eligibleForRedaction(),
                view.ineligibilityReason(),
                view.judgeVerdict() == null ? null : view.judgeVerdict().name(),
                view.judgeConfidence());
    }
}
