package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup.MasterState;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingView;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.GroupBy;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.StatusFilter;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationTotals;
import pro.softcom.aisentinel.application.pii.remediation.port.in.SelectionStatusChangeCommand;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeRequestDto.FindingStatusChangeDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationFindingDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSelectionDto;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("RemediationDtoMapper")
class RemediationDtoMapperTest {

    private final RemediationDtoMapper mapper = new RemediationDtoMapper();

    @Nested
    @DisplayName("toQuery() method tests")
    class ToQueryTests {

        @Test
        @DisplayName("Should_MapAllFields_When_RequestIsComplete")
        void Should_MapAllFields_When_RequestIsComplete() {
            RemediationSearchRequestDto request = new RemediationSearchRequestDto(
                    new RemediationScopeDto("SPACE", "p1", null),
                    "type", "PENDING", "john", "p1", 2, 25,
                    new RemediationSelectionDto(null, List.of("EMAIL"), List.of("high"),
                            List.of("excluded-1"), List.of("included-1")));

            RemediationFindingsQuery query = mapper.toQuery(request);

            assertSoftly(softly -> {
                softly.assertThat(query.spaceKey()).isEqualTo("SPACE");
                softly.assertThat(query.pageId()).isEqualTo("p1");
                softly.assertThat(query.groupBy()).isEqualTo(GroupBy.TYPE);
                softly.assertThat(query.statusFilter()).isEqualTo(StatusFilter.PENDING);
                softly.assertThat(query.searchText()).isEqualTo("john");
                softly.assertThat(query.itemFilter()).isEqualTo("p1");
                softly.assertThat(query.page()).isEqualTo(2);
                softly.assertThat(query.pageSize()).isEqualTo(25);
                softly.assertThat(query.selection().spaceKey()).isEqualTo("SPACE");
                softly.assertThat(query.selection().pageId()).isEqualTo("p1");
                softly.assertThat(query.selection().piiTypes()).containsExactly("EMAIL");
                softly.assertThat(query.selection().severities())
                        .containsExactly(PersonallyIdentifiableInformationSeverity.HIGH);
                softly.assertThat(query.selection().excludedFindingIds()).containsExactly("excluded-1");
                softly.assertThat(query.selection().includedFindingIds()).containsExactly("included-1");
            });
        }

        @Test
        @DisplayName("Should_ApplyDefaults_When_OptionalFieldsMissing")
        void Should_ApplyDefaults_When_OptionalFieldsMissing() {
            RemediationSearchRequestDto request = new RemediationSearchRequestDto(
                    new RemediationScopeDto("SPACE", null, null),
                    "severity", null, null, null, null, null, null);

            RemediationFindingsQuery query = mapper.toQuery(request);

            assertSoftly(softly -> {
                softly.assertThat(query.groupBy()).isEqualTo(GroupBy.SEVERITY);
                softly.assertThat(query.statusFilter()).isEqualTo(StatusFilter.ALL);
                softly.assertThat(query.page()).isZero();
                softly.assertThat(query.pageSize()).isEqualTo(50);
                softly.assertThat(query.selection().piiTypes()).isEmpty();
                softly.assertThat(query.selection().severities()).isEmpty();
                softly.assertThat(query.selection().excludedFindingIds()).isEmpty();
                softly.assertThat(query.selection().includedFindingIds()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should_ThrowIllegalArgumentException_When_GroupByUnsupported")
        void Should_ThrowIllegalArgumentException_When_GroupByUnsupported() {
            RemediationSearchRequestDto request = new RemediationSearchRequestDto(
                    new RemediationScopeDto("SPACE", null, null),
                    "detector", null, null, null, null, null, null);

            assertThatThrownBy(() -> mapper.toQuery(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("groupBy");
        }

        @Test
        @DisplayName("Should_ThrowIllegalArgumentException_When_ScopeMissing")
        void Should_ThrowIllegalArgumentException_When_ScopeMissing() {
            RemediationSearchRequestDto request = new RemediationSearchRequestDto(
                    null, "type", null, null, null, null, null, null);

            assertThatThrownBy(() -> mapper.toQuery(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scope");
        }
    }

    @Nested
    @DisplayName("toCommand() method tests")
    class ToCommandTests {

        @Test
        @DisplayName("Should_MapChangesAndActor_When_RequestIsValid")
        void Should_MapChangesAndActor_When_RequestIsValid() {
            FindingStatusChangeRequestDto request = new FindingStatusChangeRequestDto(
                    List.of(new FindingStatusChangeDto("f-1", "false_positive")));

            FindingStatusChangeCommand command = mapper.toCommand(request, "alice");

            assertSoftly(softly -> {
                softly.assertThat(command.actor()).isEqualTo("alice");
                softly.assertThat(command.changes()).hasSize(1);
                softly.assertThat(command.changes().getFirst().findingId()).isEqualTo("f-1");
                softly.assertThat(command.changes().getFirst().targetStatus())
                        .isEqualTo(FindingRemediationStatus.FALSE_POSITIVE);
            });
        }

        @Test
        @DisplayName("Should_ThrowIllegalArgumentException_When_TargetStatusUnknown")
        void Should_ThrowIllegalArgumentException_When_TargetStatusUnknown() {
            FindingStatusChangeRequestDto request = new FindingStatusChangeRequestDto(
                    List.of(new FindingStatusChangeDto("f-1", "OBLITERATED")));

            assertThatThrownBy(() -> mapper.toCommand(request, "alice"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should_ThrowIllegalArgumentException_When_ChangesMissing")
        void Should_ThrowIllegalArgumentException_When_ChangesMissing() {
            FindingStatusChangeRequestDto request = new FindingStatusChangeRequestDto(null);

            assertThatThrownBy(() -> mapper.toCommand(request, "alice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("changes");
        }
    }

    @Nested
    @DisplayName("toDto() method tests")
    class ToDtoTests {

        @Test
        @DisplayName("Should_MapGroupsAndTotals_When_ResultIsComplete")
        void Should_MapGroupsAndTotals_When_ResultIsComplete() {
            RemediationFindingsResult result = RemediationFindingsResult.builder()
                    .groups(List.of(RemediationFindingGroup.builder()
                            .key("EMAIL")
                            .label("Email Address")
                            .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                            .total(2)
                            .selectedCount(1)
                            .masterState(MasterState.PARTIAL)
                            .findings(List.of(findingView()))
                            .build()))
                    .totals(new RemediationTotals(3, 2, 1, 0, 6))
                    .page(1)
                    .pageSize(25)
                    .totalElements(6)
                    .nonEligibleLegacyCount(4)
                    .build();

            RemediationSearchResponseDto dto = mapper.toDto(result);

            assertSoftly(softly -> {
                softly.assertThat(dto.groups()).hasSize(1);
                softly.assertThat(dto.groups().getFirst().key()).isEqualTo("EMAIL");
                softly.assertThat(dto.groups().getFirst().severity()).isEqualTo("MEDIUM");
                softly.assertThat(dto.groups().getFirst().masterState()).isEqualTo("partial");
                softly.assertThat(dto.groups().getFirst().findings().getFirst().status()).isEqualTo("PENDING");
                softly.assertThat(dto.groups().getFirst().findings().getFirst().severity()).isEqualTo("MEDIUM");
                softly.assertThat(dto.groups().getFirst().findings().getFirst().sensitiveValue())
                        .isEqualTo("secret-value");
                softly.assertThat(dto.groups().getFirst().findings().getFirst().sensitiveContext())
                        .isEqualTo("secret-context");
                softly.assertThat(dto.totals().pending()).isEqualTo(3);
                softly.assertThat(dto.totals().handled()).isEqualTo(2);
                softly.assertThat(dto.totals().falsePositive()).isEqualTo(1);
                softly.assertThat(dto.totals().total()).isEqualTo(6);
                softly.assertThat(dto.page()).isEqualTo(1);
                softly.assertThat(dto.pageSize()).isEqualTo(25);
                softly.assertThat(dto.totalElements()).isEqualTo(6);
                softly.assertThat(dto.nonEligibleLegacyCount()).isEqualTo(4);
            });
        }

        @Test
        @DisplayName("Should_MapAppliedAndRejected_When_StatusResultReturned")
        void Should_MapAppliedAndRejected_When_StatusResultReturned() {
            FindingStatusChangeResult result = new FindingStatusChangeResult(
                    List.of("f-1"),
                    List.of(new FindingStatusChangeResult.RejectedChange("f-2", "illegal transition")));

            FindingStatusChangeResponseDto dto = mapper.toDto(result);

            assertSoftly(softly -> {
                softly.assertThat(dto.applied()).containsExactly("f-1");
                softly.assertThat(dto.rejected()).hasSize(1);
                softly.assertThat(dto.rejected().getFirst().findingId()).isEqualTo("f-2");
                softly.assertThat(dto.rejected().getFirst().reason()).isEqualTo("illegal transition");
            });
        }

        @Test
        @DisplayName("Should_ExposeSensitiveValueAndContextButNotSourceContent_When_FindingDtoShapeInspected")
        void Should_ExposeSensitiveValueAndContextButNotSourceContent_When_FindingDtoShapeInspected() {
            List<String> componentNames = Arrays.stream(RemediationFindingDto.class.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();

            assertThat(componentNames)
                    .contains("maskedContext", "sensitiveValue", "sensitiveContext")
                    .doesNotContain("sourceContent");
        }

        private RemediationFindingView findingView() {
            return RemediationFindingView.builder()
                    .findingId("f-1")
                    .piiType("EMAIL")
                    .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                    .detector("PRESIDIO")
                    .confidenceScore(0.9)
                    .maskedContext("masked")
                    .sensitiveValue("secret-value")
                    .sensitiveContext("secret-context")
                    .pageId("p1")
                    .pageTitle("Alpha Page")
                    .status(FindingRemediationStatus.PENDING)
                    .selected(true)
                    .eligibleForRedaction(true)
                    .build();
        }
    }

    @Nested
    @DisplayName("toSelectionCommand() method tests")
    class ToSelectionCommandTests {

        @Test
        @DisplayName("Should_BuildCommandWithParsedStatus_When_TargetStatusValid")
        void Should_BuildCommandWithParsedStatus_When_TargetStatusValid() {
            RemediationSelection selection = RemediationSelection.builder().spaceKey("SPACE").build();

            SelectionStatusChangeCommand command =
                    mapper.toSelectionCommand(selection, "manually_handled", "alice");

            assertSoftly(softly -> {
                softly.assertThat(command.selection()).isSameAs(selection);
                softly.assertThat(command.targetStatus())
                        .isEqualTo(FindingRemediationStatus.MANUALLY_HANDLED);
                softly.assertThat(command.actor()).isEqualTo("alice");
            });
        }

        @Test
        @DisplayName("Should_Throw_When_TargetStatusMissing")
        void Should_Throw_When_TargetStatusMissing() {
            RemediationSelection selection = RemediationSelection.builder().spaceKey("SPACE").build();

            assertThatThrownBy(() -> mapper.toSelectionCommand(selection, " ", "alice"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
