package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jspecify.annotations.NonNull;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ChangeFindingStatusPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ExecuteObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ExecuteObfuscationPort.ObfuscationSubmission;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.PlanObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.QueryRemediationFindingsPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.GroupBy;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.StatusFilter;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationTotals;
import pro.softcom.aisentinel.application.pii.remediation.port.in.SelectionStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.TrackObfuscationJobPort;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJob;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobNotFoundException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobStatus;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationPlan;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.remediation.SelectionOutdatedException;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobCreatedDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobStatusDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationPlanDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationConfigDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSelectionDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationTotalsDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.SelectionStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationDtoMapper;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationJobDtoMapper;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiRemediationController - REST controller for PII remediation")
class PiiRemediationControllerTest {

    @Mock
    private QueryRemediationFindingsPort queryRemediationFindingsPort;

    @Mock
    private ChangeFindingStatusPort changeFindingStatusPort;

    @Mock
    private PlanObfuscationPort planObfuscationPort;

    @Mock
    private ExecuteObfuscationPort executeObfuscationPort;

    @Mock
    private TrackObfuscationJobPort trackObfuscationJobPort;

    @Mock
    private RemediationDtoMapper mapper;

    private PiiRemediationController controller;

    @BeforeEach
    void setUp() {
        controller = new PiiRemediationController(queryRemediationFindingsPort, changeFindingStatusPort,
                planObfuscationPort, executeObfuscationPort, trackObfuscationJobPort,
                mapper, new RemediationJobDtoMapper());
    }

    @Nested
    @DisplayName("getConfig() method tests")
    class GetConfigTests {

        @Test
        @DisplayName("Should_ReturnEnabledTrue_When_FeatureFlagOn")
        void Should_ReturnEnabledTrue_When_FeatureFlagOn() {
            when(queryRemediationFindingsPort.isRemediationEnabled()).thenReturn(true);

            ResponseEntity<@NonNull RemediationConfigDto> response = controller.getConfig();

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isEqualTo(new RemediationConfigDto(true));
            });
        }

        @Test
        @DisplayName("Should_ReturnEnabledFalse_When_FeatureFlagOff")
        void Should_ReturnEnabledFalse_When_FeatureFlagOff() {
            when(queryRemediationFindingsPort.isRemediationEnabled()).thenReturn(false);

            ResponseEntity<@NonNull RemediationConfigDto> response = controller.getConfig();

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isEqualTo(new RemediationConfigDto(false));
            });
        }
    }

    @Nested
    @DisplayName("searchFindings() method tests")
    class SearchFindingsTests {

        @Test
        @DisplayName("Should_ReturnMappedView_When_SearchSucceeds")
        void Should_ReturnMappedView_When_SearchSucceeds() {
            RemediationSearchRequestDto request = searchRequest();
            RemediationFindingsQuery query = query();
            RemediationFindingsResult result = emptyResult();
            RemediationSearchResponseDto responseDto = new RemediationSearchResponseDto(
                    List.of(), new RemediationTotalsDto(0, 0, 0, 0), 0, 50, 0, 0, 0);
            when(mapper.toQuery(request)).thenReturn(query);
            when(queryRemediationFindingsPort.search(query)).thenReturn(result);
            when(mapper.toDto(result)).thenReturn(responseDto);

            ResponseEntity<@NonNull RemediationSearchResponseDto> response = controller.searchFindings(request);

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isSameAs(responseDto);
            });
        }

        @Test
        @DisplayName("Should_PropagateRemediationDisabledException_When_UseCaseGuardRejects")
        void Should_PropagateRemediationDisabledException_When_UseCaseGuardRejects() {
            RemediationSearchRequestDto request = searchRequest();
            RemediationFindingsQuery query = query();
            when(mapper.toQuery(request)).thenReturn(query);
            when(queryRemediationFindingsPort.search(query))
                    .thenThrow(new RemediationDisabledException("disabled"));

            assertThatThrownBy(() -> controller.searchFindings(request))
                    .isInstanceOf(RemediationDisabledException.class);
        }
    }

    @Nested
    @DisplayName("changeFindingStatuses() method tests")
    class ChangeFindingStatusesTests {

        @Test
        @DisplayName("Should_UsePrincipalNameAsActor_When_PrincipalPresent")
        void Should_UsePrincipalNameAsActor_When_PrincipalPresent() {
            FindingStatusChangeRequestDto request = new FindingStatusChangeRequestDto(List.of());
            FindingStatusChangeCommand command = new FindingStatusChangeCommand(List.of(), "alice");
            FindingStatusChangeResult result = new FindingStatusChangeResult(List.of(), List.of());
            FindingStatusChangeResponseDto responseDto = new FindingStatusChangeResponseDto(List.of(), List.of());
            Principal principal = () -> "alice";
            when(mapper.toCommand(request, "alice")).thenReturn(command);
            when(changeFindingStatusPort.changeStatuses(command)).thenReturn(result);
            when(mapper.toDto(result)).thenReturn(responseDto);

            ResponseEntity<@NonNull FindingStatusChangeResponseDto> response =
                    controller.changeFindingStatuses(request, principal);

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isSameAs(responseDto);
            });
            verify(mapper).toCommand(request, "alice");
        }

        @Test
        @DisplayName("Should_FallBackToSystemActor_When_PrincipalMissing")
        void Should_FallBackToSystemActor_When_PrincipalMissing() {
            FindingStatusChangeRequestDto request = new FindingStatusChangeRequestDto(List.of());
            FindingStatusChangeCommand command = new FindingStatusChangeCommand(List.of(), "system");
            FindingStatusChangeResult result = new FindingStatusChangeResult(List.of(), List.of());
            when(mapper.toCommand(request, "system")).thenReturn(command);
            when(changeFindingStatusPort.changeStatuses(command)).thenReturn(result);
            when(mapper.toDto(result)).thenReturn(new FindingStatusChangeResponseDto(List.of(), List.of()));

            controller.changeFindingStatuses(request, null);

            verify(mapper).toCommand(request, "system");
        }
    }

    @Nested
    @DisplayName("changeFindingStatusesBySelection() method tests")
    class ChangeFindingStatusesBySelectionTests {

        @Test
        @DisplayName("Should_ResolveSelectionAndTransition_When_Requested")
        void Should_ResolveSelectionAndTransition_When_Requested() {
            SelectionStatusChangeRequestDto request =
                    new SelectionStatusChangeRequestDto(selectionDto(), "MANUALLY_HANDLED");
            SelectionStatusChangeCommand command = new SelectionStatusChangeCommand(
                    RemediationSelection.builder().spaceKey("SPACE").build(),
                    FindingRemediationStatus.MANUALLY_HANDLED, "alice");
            FindingStatusChangeResult result = new FindingStatusChangeResult(List.of("f-1"), List.of());
            FindingStatusChangeResponseDto responseDto =
                    new FindingStatusChangeResponseDto(List.of("f-1"), List.of());
            Principal principal = () -> "alice";
            when(mapper.toSelectionCommand(any(RemediationSelection.class), any(), any())).thenReturn(command);
            when(changeFindingStatusPort.changeStatusesBySelection(command)).thenReturn(result);
            when(mapper.toDto(result)).thenReturn(responseDto);

            ResponseEntity<@NonNull FindingStatusChangeResponseDto> response =
                    controller.changeFindingStatusesBySelection(request, principal);

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody()).isSameAs(responseDto);
            });
            verify(changeFindingStatusPort).changeStatusesBySelection(command);
        }
    }

    @Nested
    @DisplayName("plan() method tests")
    class PlanTests {

        @Test
        @DisplayName("Should_ReturnBackendComputedPlan_When_SelectionSubmitted")
        void Should_ReturnBackendComputedPlan_When_SelectionSubmitted() {
            when(planObfuscationPort.plan(any(RemediationSelection.class)))
                    .thenReturn(ObfuscationPlan.builder()
                            .totalFindings(2)
                            .bySeverity(Map.of())
                            .pagesImpacted(1)
                            .falsePositivesReported(0)
                            .attachmentExclusions(1)
                            .selectionChecksum("checksum-1")
                            .build());

            ResponseEntity<@NonNull ObfuscationPlanDto> response = controller.plan(selectionDto());

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody().totalFindings()).isEqualTo(2);
                softly.assertThat(response.getBody().selectionChecksum()).isEqualTo("checksum-1");
            });
        }
    }

    @Nested
    @DisplayName("submitJob() method tests")
    class SubmitJobTests {

        @Test
        @DisplayName("Should_ReturnAcceptedWithJobId_When_SubmissionSucceeds")
        void Should_ReturnAcceptedWithJobId_When_SubmissionSucceeds() {
            when(executeObfuscationPort.execute(any(ObfuscationSubmission.class))).thenReturn("job-1");

            ResponseEntity<@NonNull ObfuscationJobCreatedDto> response =
                    controller.submitJob(jobRequest(), () -> "officer");

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                softly.assertThat(response.getBody().jobId()).isEqualTo("job-1");
            });
        }

        @Test
        @DisplayName("Should_FallBackToSystemActor_When_NoPrincipal")
        void Should_FallBackToSystemActor_When_NoPrincipal() {
            when(executeObfuscationPort.execute(any(ObfuscationSubmission.class))).thenReturn("job-1");

            controller.submitJob(jobRequest(), null);

            verify(executeObfuscationPort).execute(
                    argThat(submission -> "system".equals(submission.actor())));
        }

        @Test
        @DisplayName("Should_PropagateSelectionOutdated_When_ChecksumDiverged")
        void Should_PropagateSelectionOutdated_When_ChecksumDiverged() {
            when(executeObfuscationPort.execute(any(ObfuscationSubmission.class)))
                    .thenThrow(new SelectionOutdatedException("selection changed"));
            ObfuscationJobRequestDto request = jobRequest();
            Principal principal = () -> "officer";

            assertThatThrownBy(() -> controller.submitJob(request, principal))
                    .isInstanceOf(SelectionOutdatedException.class);
        }
    }

    @Nested
    @DisplayName("getJob() method tests")
    class GetJobTests {

        @Test
        @DisplayName("Should_ReturnJobStatus_When_JobExists")
        void Should_ReturnJobStatus_When_JobExists() {
            when(trackObfuscationJobPort.findJob("job-1")).thenReturn(Optional.of(ObfuscationJob.builder()
                    .id("job-1")
                    .spaceKey("SPACE")
                    .status(ObfuscationJobStatus.RUNNING)
                    .submittedSelection(RemediationSelection.builder().spaceKey("SPACE").build())
                    .processed(1)
                    .total(3)
                    .actor("officer")
                    .createdAt(Instant.parse("2026-07-06T10:00:00Z"))
                    .updatedAt(Instant.parse("2026-07-06T10:01:00Z"))
                    .build()));

            ResponseEntity<@NonNull ObfuscationJobStatusDto> response = controller.getJob("job-1");

            assertSoftly(softly -> {
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                softly.assertThat(response.getBody().status()).isEqualTo("RUNNING");
                softly.assertThat(response.getBody().processed()).isEqualTo(1);
                softly.assertThat(response.getBody().total()).isEqualTo(3);
            });
        }

        @Test
        @DisplayName("Should_ThrowObfuscationJobNotFound_When_JobUnknown")
        void Should_ThrowObfuscationJobNotFound_When_JobUnknown() {
            when(trackObfuscationJobPort.findJob("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getJob("unknown"))
                    .isInstanceOf(ObfuscationJobNotFoundException.class);
        }
    }

    private static RemediationSelectionDto selectionDto() {
        return new RemediationSelectionDto(new RemediationScopeDto("SPACE", null, null),
                List.of("EMAIL"), null, null, null);
    }

    private static ObfuscationJobRequestDto jobRequest() {
        return new ObfuscationJobRequestDto(selectionDto(), "checksum-1");
    }

    private static RemediationSearchRequestDto searchRequest() {
        return new RemediationSearchRequestDto(new RemediationScopeDto("SPACE", null, null),
                "type", "ALL", null, null, 0, 50, null);
    }

    private static RemediationFindingsQuery query() {
        return RemediationFindingsQuery.builder()
                .spaceKey("SPACE")
                .groupBy(GroupBy.TYPE)
                .statusFilter(StatusFilter.ALL)
                .page(0)
                .pageSize(50)
                .selection(RemediationSelection.builder().spaceKey("SPACE").build())
                .build();
    }

    private static RemediationFindingsResult emptyResult() {
        return RemediationFindingsResult.builder()
                .groups(List.of())
                .totals(RemediationTotals.empty())
                .page(0)
                .pageSize(50)
                .totalElements(0)
                .nonEligibleLegacyCount(0)
                .build();
    }
}
