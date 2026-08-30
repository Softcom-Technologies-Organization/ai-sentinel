package pro.softcom.aisentinel.application.pii.remediation.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeCommand.StatusChange;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.SelectionStatusChangeCommand;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.EligibleFinding;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver.ResolvedSelection;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.remediation.SpaceFalsePositivesChanged;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeFindingStatusUseCase")
class ChangeFindingStatusUseCaseTest {

    private static final String SPACE = "SPACE";
    private static final String SCAN_ID = "scan-1";
    private static final String ACTOR = "alice";
    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");

    @Mock
    private RemediationConfigPort remediationConfigPort;

    @Mock
    private ScanResultQuery scanResultQuery;

    @Mock
    private FindingRemediationStore findingRemediationStore;

    @Mock
    private SeverityCalculationService severityCalculationService;

    @Mock
    private SelectionResolver selectionResolver;

    @Mock
    private PublishRemediationEventPort publishRemediationEventPort;

    @Captor
    private ArgumentCaptor<Collection<FindingRemediation>> upsertCaptor;

    private ChangeFindingStatusUseCase useCase;

    private String emailFindingId;

    @BeforeEach
    void setUp() {
        useCase = new ChangeFindingStatusUseCase(remediationConfigPort, scanResultQuery,
                findingRemediationStore, new ScanEventFindingResolver(severityCalculationService),
                selectionResolver, publishRemediationEventPort, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
        lenient().when(severityCalculationService.calculateSeverity("EMAIL"))
                .thenReturn(PersonallyIdentifiableInformationSeverity.MEDIUM);
        emailFindingId = emailReference().findingId();
    }

    @Nested
    @DisplayName("changeStatuses() guard tests")
    class GuardTests {

        @Test
        @DisplayName("Should_ThrowRemediationDisabledException_When_FeatureFlagOff")
        void Should_ThrowRemediationDisabledException_When_FeatureFlagOff() {
            when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
            FindingStatusChangeCommand command = command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.FALSE_POSITIVE));

            assertThatThrownBy(() -> useCase.changeStatuses(command))
                    .isInstanceOf(RemediationDisabledException.class);
            verifyNoInteractions(findingRemediationStore);
        }
    }

    @Nested
    @DisplayName("changeStatuses() first transition tests")
    class FirstTransitionTests {

        @Test
        @DisplayName("Should_CreateFalsePositiveRow_When_PendingFindingReported")
        void Should_CreateFalsePositiveRow_When_PendingFindingReported() {
            when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of());
            when(scanResultQuery.findLatestScan())
                    .thenReturn(Optional.of(new LastScanMeta(SCAN_ID, NOW, 1)));
            when(scanResultQuery.listItemEventsEncrypted(SCAN_ID)).thenReturn(List.of(emailEvent()));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.FALSE_POSITIVE)));

            verify(findingRemediationStore).upsertAll(upsertCaptor.capture());
            FindingRemediation row = upsertCaptor.getValue().iterator().next();
            assertSoftly(softly -> {
                softly.assertThat(result.applied()).containsExactly(emailFindingId);
                softly.assertThat(result.rejected()).isEmpty();
                softly.assertThat(row.findingId()).isEqualTo(emailFindingId);
                softly.assertThat(row.scanId()).isEqualTo(SCAN_ID);
                softly.assertThat(row.spaceKey()).isEqualTo(SPACE);
                softly.assertThat(row.pageId()).isEqualTo("p1");
                softly.assertThat(row.piiType()).isEqualTo("EMAIL");
                softly.assertThat(row.severity()).isEqualTo(PersonallyIdentifiableInformationSeverity.MEDIUM);
                softly.assertThat(row.detector()).isEqualTo("PRESIDIO");
                softly.assertThat(row.status()).isEqualTo(FindingRemediationStatus.FALSE_POSITIVE);
                softly.assertThat(row.actor()).isEqualTo(ACTOR);
                softly.assertThat(row.occurredAt()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("Should_RejectUnknownFinding_When_IdNotResolvableFromLatestScan")
        void Should_RejectUnknownFinding_When_IdNotResolvableFromLatestScan() {
            when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of());
            when(scanResultQuery.findLatestScan())
                    .thenReturn(Optional.of(new LastScanMeta(SCAN_ID, NOW, 1)));
            when(scanResultQuery.listItemEventsEncrypted(SCAN_ID)).thenReturn(List.of());

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange("unknown-id", FindingRemediationStatus.FALSE_POSITIVE)));

            assertSoftly(softly -> {
                softly.assertThat(result.applied()).isEmpty();
                softly.assertThat(result.rejected()).hasSize(1);
                softly.assertThat(result.rejected().getFirst().findingId()).isEqualTo("unknown-id");
                softly.assertThat(result.rejected().getFirst().reason())
                        .isEqualTo("error.remediation.finding_not_found");
            });
            verify(findingRemediationStore, never()).upsertAll(anyCollection());
        }

        @Test
        @DisplayName("Should_RejectPendingToPending_When_NoRowExists")
        void Should_RejectPendingToPending_When_NoRowExists() {
            when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of());
            when(scanResultQuery.findLatestScan())
                    .thenReturn(Optional.of(new LastScanMeta(SCAN_ID, NOW, 1)));
            when(scanResultQuery.listItemEventsEncrypted(SCAN_ID)).thenReturn(List.of(emailEvent()));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.PENDING)));

            assertThat(result.rejected().getFirst().reason())
                    .isEqualTo("error.remediation.invalid_status_transition");
        }
    }

    @Nested
    @DisplayName("changeStatuses() existing row tests")
    class ExistingRowTests {

        @Test
        @DisplayName("Should_RestoreToPending_When_FalsePositiveRowExists")
        void Should_RestoreToPending_When_FalsePositiveRowExists() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.FALSE_POSITIVE)));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.PENDING)));

            verify(findingRemediationStore).upsertAll(upsertCaptor.capture());
            FindingRemediation row = upsertCaptor.getValue().iterator().next();
            assertSoftly(softly -> {
                softly.assertThat(result.applied()).containsExactly(emailFindingId);
                softly.assertThat(row.status()).isEqualTo(FindingRemediationStatus.PENDING);
                softly.assertThat(row.actor()).isEqualTo(ACTOR);
                softly.assertThat(row.occurredAt()).isEqualTo(NOW);
            });
            verifyNoInteractions(scanResultQuery);
        }

        @Test
        @DisplayName("Should_RejectTransition_When_FindingAlreadyRedacted")
        void Should_RejectTransition_When_FindingAlreadyRedacted() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.REDACTED)));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.PENDING)));

            assertSoftly(softly -> {
                softly.assertThat(result.applied()).isEmpty();
                softly.assertThat(result.rejected().getFirst().reason())
                        .isEqualTo("error.remediation.invalid_status_transition");
            });
            verify(findingRemediationStore, never()).upsertAll(anyCollection());
        }
    }

    @Nested
    @DisplayName("changeStatuses() batch tests")
    class BatchTests {

        @Test
        @DisplayName("Should_RejectRedactedTarget_When_RequestedThroughStatusEndpoint")
        void Should_RejectRedactedTarget_When_RequestedThroughStatusEndpoint() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.PENDING)));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.REDACTED)));

            assertSoftly(softly -> {
                softly.assertThat(result.applied()).isEmpty();
                softly.assertThat(result.rejected().getFirst().reason())
                        .isEqualTo("error.remediation.redacted_status_reserved");
            });
            verify(findingRemediationStore, never()).upsertAll(anyCollection());
        }

        @Test
        @DisplayName("Should_ProcessBatchIndependently_When_MixedValidAndInvalidChanges")
        void Should_ProcessBatchIndependently_When_MixedValidAndInvalidChanges() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.MANUALLY_HANDLED)));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.PENDING),
                    new StatusChange(emailFindingId, FindingRemediationStatus.REDACTED)));

            verify(findingRemediationStore).upsertAll(upsertCaptor.capture());
            assertSoftly(softly -> {
                softly.assertThat(result.applied()).containsExactly(emailFindingId);
                softly.assertThat(result.rejected()).hasSize(1);
                softly.assertThat(upsertCaptor.getValue()).hasSize(1);
            });
        }
    }

    @Nested
    @DisplayName("changeStatusesBySelection()")
    class BySelectionTests {

        @Test
        @DisplayName("Should_TransitionEveryResolvedPendingFinding_When_SelectionMarkedTreated")
        void Should_TransitionEveryResolvedPendingFinding_When_SelectionMarkedTreated() {
            RemediationSelection selection = RemediationSelection.builder()
                    .spaceKey(SPACE)
                    .severities(List.of(PersonallyIdentifiableInformationSeverity.MEDIUM))
                    .build();
            when(selectionResolver.resolve(selection)).thenReturn(new ResolvedSelection(SCAN_ID,
                    List.of(eligible("f-1"), eligible("f-2")), List.of(eligible("f-3")), 0));
            when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of(
                    rowWithId("f-1", FindingRemediationStatus.PENDING),
                    rowWithId("f-2", FindingRemediationStatus.PENDING),
                    rowWithId("f-3", FindingRemediationStatus.PENDING)));

            FindingStatusChangeResult result = useCase.changeStatusesBySelection(
                    new SelectionStatusChangeCommand(selection,
                            FindingRemediationStatus.MANUALLY_HANDLED, ACTOR));

            verify(findingRemediationStore).upsertAll(upsertCaptor.capture());
            assertSoftly(softly -> {
                softly.assertThat(result.applied()).containsExactlyInAnyOrder("f-1", "f-2", "f-3");
                softly.assertThat(upsertCaptor.getValue()).hasSize(3);
                softly.assertThat(upsertCaptor.getValue()).allSatisfy(row ->
                        assertThat(row.status()).isEqualTo(FindingRemediationStatus.MANUALLY_HANDLED));
            });
        }

        @Test
        @DisplayName("Should_ThrowRemediationDisabledException_When_FeatureFlagOff")
        void Should_ThrowRemediationDisabledException_When_FeatureFlagOff() {
            when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
            RemediationSelection selection = RemediationSelection.builder().spaceKey(SPACE).build();
            SelectionStatusChangeCommand command = new SelectionStatusChangeCommand(selection,
                    FindingRemediationStatus.MANUALLY_HANDLED, ACTOR);

            assertThatThrownBy(() -> useCase.changeStatusesBySelection(command))
                    .isInstanceOf(RemediationDisabledException.class);
            verifyNoInteractions(selectionResolver, findingRemediationStore);
        }
    }

    @Nested
    @DisplayName("false-positive change publication")
    class FalsePositiveEventTests {

        @Test
        @DisplayName("Should_PublishSpaceFalsePositivesChanged_When_FindingReportedAsFalsePositive")
        void Should_PublishSpaceFalsePositivesChanged_When_FindingReportedAsFalsePositive() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.PENDING)));

            useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.FALSE_POSITIVE)));

            verify(publishRemediationEventPort)
                    .publishFalsePositivesChanged(new SpaceFalsePositivesChanged(SCAN_ID, SPACE));
        }

        @Test
        @DisplayName("Should_PublishSpaceFalsePositivesChanged_When_FalsePositiveRestored")
        void Should_PublishSpaceFalsePositivesChanged_When_FalsePositiveRestored() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.FALSE_POSITIVE)));

            useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.PENDING)));

            verify(publishRemediationEventPort)
                    .publishFalsePositivesChanged(new SpaceFalsePositivesChanged(SCAN_ID, SPACE));
        }

        @Test
        @DisplayName("Should_NotPublish_When_AppliedChangesDoNotTouchFalsePositives")
        void Should_NotPublish_When_AppliedChangesDoNotTouchFalsePositives() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.PENDING)));

            useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.MANUALLY_HANDLED)));

            verifyNoInteractions(publishRemediationEventPort);
        }

        @Test
        @DisplayName("Should_NotPublish_When_FalsePositiveChangeRejected")
        void Should_NotPublish_When_FalsePositiveChangeRejected() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.REDACTED)));

            useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.FALSE_POSITIVE)));

            verifyNoInteractions(publishRemediationEventPort);
        }

        @Test
        @DisplayName("Should_PublishOncePerScanAndSpace_When_SeveralFindingsOfSameSpaceReported")
        void Should_PublishOncePerScanAndSpace_When_SeveralFindingsOfSameSpaceReported() {
            when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of(
                    rowWithId("f-1", FindingRemediationStatus.PENDING),
                    rowWithId("f-2", FindingRemediationStatus.PENDING)));

            useCase.changeStatuses(command(
                    new StatusChange("f-1", FindingRemediationStatus.FALSE_POSITIVE),
                    new StatusChange("f-2", FindingRemediationStatus.FALSE_POSITIVE)));

            verify(publishRemediationEventPort, times(1))
                    .publishFalsePositivesChanged(new SpaceFalsePositivesChanged(SCAN_ID, SPACE));
        }

        @Test
        @DisplayName("Should_KeepStatusChangeApplied_When_PublicationFails")
        void Should_KeepStatusChangeApplied_When_PublicationFails() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(existingRow(FindingRemediationStatus.PENDING)));
            doThrow(new IllegalStateException("broker down")).when(publishRemediationEventPort)
                    .publishFalsePositivesChanged(new SpaceFalsePositivesChanged(SCAN_ID, SPACE));

            FindingStatusChangeResult result = useCase.changeStatuses(command(
                    new StatusChange(emailFindingId, FindingRemediationStatus.FALSE_POSITIVE)));

            assertThat(result.applied()).containsExactly(emailFindingId);
            verify(findingRemediationStore).upsertAll(anyCollection());
        }
    }

    private static EligibleFinding eligible(String findingId) {
        return EligibleFinding.builder()
                .findingId(findingId)
                .reference(emailReference())
                .confidence(0.9)
                .piiTypeLabel("Email Address")
                .maskedContext("masked-email")
                .pageTitle("Alpha Page")
                .build();
    }

    private FindingRemediation rowWithId(String findingId, FindingRemediationStatus status) {
        return FindingRemediation.builder()
                .findingId(findingId)
                .scanId(SCAN_ID)
                .spaceKey(SPACE)
                .pageId("p1")
                .piiType("EMAIL")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .detector("PRESIDIO")
                .status(status)
                .actor("bob")
                .occurredAt(NOW.minusSeconds(3600))
                .build();
    }

    private static FindingStatusChangeCommand command(StatusChange... changes) {
        return new FindingStatusChangeCommand(List.of(changes), ACTOR);
    }

    private static FindingReference emailReference() {
        return FindingReference.builder()
                .spaceKey(SPACE)
                .pageId("p1")
                .detector("PRESIDIO")
                .piiType("EMAIL")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .valueFingerprint("fp-email-1")
                .build();
    }

    private FindingRemediation existingRow(FindingRemediationStatus status) {
        return FindingRemediation.builder()
                .findingId(emailFindingId)
                .scanId(SCAN_ID)
                .spaceKey(SPACE)
                .pageId("p1")
                .piiType("EMAIL")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .detector("PRESIDIO")
                .status(status)
                .actor("bob")
                .occurredAt(NOW.minusSeconds(3600))
                .build();
    }

    private static ConfluenceContentScanResult emailEvent() {
        DetectedPersonallyIdentifiableInformation detection = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(0)
                .endPosition(10)
                .piiType("EMAIL")
                .piiTypeLabel("Email Address")
                .confidence(0.9)
                .sensitiveValue("ENC:v1:opaque")
                .sensitiveContext("ENC:v1:context")
                .maskedContext("masked-email")
                .source(DetectorSource.PRESIDIO)
                .valueFingerprint("fp-email-1")
                .build();
        return ConfluenceContentScanResult.builder()
                .scanId(SCAN_ID)
                .spaceKey(SPACE)
                .eventType("item")
                .pageId("p1")
                .pageTitle("Alpha Page")
                .detectedPIIs(List.of(detection))
                .build();
    }
}
