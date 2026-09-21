package pro.softcom.aisentinel.application.pii.remediation.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingGroup.MasterState;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingView;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.GroupBy;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsQuery.StatusFilter;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionEvaluator;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.remediation.FindingReference;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationDisabledException;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryRemediationFindingsUseCase")
class QueryRemediationFindingsUseCaseTest {

    private static final String SPACE = "SPACE";
    private static final String SCAN_ID = "scan-1";

    @Mock
    private RemediationConfigPort remediationConfigPort;

    @Mock
    private ScanResultQuery scanResultQuery;

    @Mock
    private ScanCheckpointRepository checkpointRepository;

    @Mock
    private FindingRemediationStore findingRemediationStore;

    @Mock
    private SeverityCalculationService severityCalculationService;

    private QueryRemediationFindingsUseCase useCase;

    private String emailP1Id;
    private String phoneP1Id;
    private String emailP2Id;
    private String ibanP2Id;
    private String avsAttachmentId;

    @BeforeEach
    void setUp() {
        useCase = new QueryRemediationFindingsUseCase(remediationConfigPort, scanResultQuery, checkpointRepository,
                findingRemediationStore, new ScanEventFindingResolver(severityCalculationService),
                new SelectionEvaluator());
        lenient().when(remediationConfigPort.isRemediationEnabled()).thenReturn(true);
        lenient().when(severityCalculationService.calculateSeverity("EMAIL"))
                .thenReturn(PersonallyIdentifiableInformationSeverity.MEDIUM);
        lenient().when(severityCalculationService.calculateSeverity("PHONE"))
                .thenReturn(PersonallyIdentifiableInformationSeverity.LOW);
        lenient().when(severityCalculationService.calculateSeverity("IBAN"))
                .thenReturn(PersonallyIdentifiableInformationSeverity.HIGH);
        lenient().when(severityCalculationService.calculateSeverity("AVS"))
                .thenReturn(PersonallyIdentifiableInformationSeverity.HIGH);
        lenient().when(checkpointRepository.findLatestBySpace(SPACE))
                .thenReturn(Optional.of(ScanCheckpoint.builder().scanId(SCAN_ID).spaceKey(SPACE).build()));
        lenient().when(scanResultQuery.listItemEventsDecryptedByScanIdAndSpaceKey(
                        SCAN_ID, SPACE, AccessPurpose.USER_DISPLAY))
                .thenReturn(defaultEvents());
        lenient().when(findingRemediationStore.findByIds(anyCollection())).thenReturn(List.of());
        emailP1Id = findingId("p1", null, "PRESIDIO", "EMAIL", "fp-email-1");
        phoneP1Id = findingId("p1", null, "REGEX", "PHONE", "fp-phone-1");
        emailP2Id = findingId("p2", null, "MINISTRAL", "EMAIL", "fp-email-2");
        ibanP2Id = findingId("p2", null, "REGEX", "IBAN", "fp-iban-1");
        avsAttachmentId = findingId("p2", "doc.pdf", "MINISTRAL", "AVS", "fp-avs-1");
    }

    @Test
    void Should_KeepSpaceResults_When_AnotherSpaceWasScannedLater() {
        lenient().when(checkpointRepository.findLatestBySpace(SPACE))
                .thenReturn(Optional.of(ScanCheckpoint.builder().scanId(SCAN_ID).spaceKey(SPACE).build()));
        lenient().when(scanResultQuery.findLatestScan())
                .thenReturn(Optional.of(new LastScanMeta("scan-other", Instant.now(), 1)));

        RemediationFindingsResult result = useCase.search(query().build());

        assertThat(result.totalElements()).isEqualTo(5);
    }

    @Test
    void Should_ReturnEmptyResults_When_SpaceHasNoCheckpoint() {
        when(checkpointRepository.findLatestBySpace(SPACE)).thenReturn(Optional.empty());
        lenient().when(scanResultQuery.findLatestScan())
                .thenReturn(Optional.of(new LastScanMeta("scan-other", Instant.now(), 1)));

        assertThat(useCase.search(query().build()).totalElements()).isZero();
        verify(scanResultQuery, never()).findLatestScan();
    }

    @Test
    void Should_ReplaceOldResults_When_SpaceHasANewerScan() {
        when(checkpointRepository.findLatestBySpace(SPACE))
                .thenReturn(Optional.of(ScanCheckpoint.builder().scanId("rescan").spaceKey(SPACE).build()));
        when(scanResultQuery.listItemEventsDecryptedByScanIdAndSpaceKey("rescan", SPACE, AccessPurpose.USER_DISPLAY))
                .thenReturn(List.of(defaultEvents().getLast().toBuilder().scanId("rescan").build()));

        RemediationFindingsResult result = useCase.search(query().build());

        assertThat(allViews(result)).extracting(RemediationFindingView::findingId).containsExactly(avsAttachmentId);
        verify(scanResultQuery, never()).listItemEventsDecryptedByScanIdAndSpaceKey(SCAN_ID, SPACE, AccessPurpose.USER_DISPLAY);
    }

    @Test
    void Should_NotRestoreOldResults_When_NewScanHasNoEventsYet() {
        when(checkpointRepository.findLatestBySpace(SPACE))
                .thenReturn(Optional.of(ScanCheckpoint.builder().scanId("rescan").spaceKey(SPACE)
                        .scanStatus(ScanStatus.NOT_STARTED).build()));

        assertThat(useCase.search(query().build()).totalElements()).isZero();
        verify(scanResultQuery, never()).listItemEventsDecryptedByScanIdAndSpaceKey(SCAN_ID, SPACE, AccessPurpose.USER_DISPLAY);
    }

    @Nested
    @DisplayName("isRemediationEnabled() method tests")
    class IsRemediationEnabledTests {

        @Test
        @DisplayName("Should_ReturnConfigValue_When_Called")
        void Should_ReturnConfigValue_When_Called() {
            when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);

            assertThat(useCase.isRemediationEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("search() guard tests")
    class GuardTests {

        @Test
        @DisplayName("Should_ThrowRemediationDisabledException_When_FeatureFlagOff")
        void Should_ThrowRemediationDisabledException_When_FeatureFlagOff() {
            when(remediationConfigPort.isRemediationEnabled()).thenReturn(false);
            RemediationFindingsQuery disabledQuery = query().build();

            assertThatThrownBy(() -> useCase.search(disabledQuery))
                    .isInstanceOf(RemediationDisabledException.class);
        }

        @Test
        @DisplayName("Should_ReturnEmptyResult_When_NoScanExists")
        void Should_ReturnEmptyResult_When_NoScanExists() {
            when(checkpointRepository.findLatestBySpace(SPACE)).thenReturn(Optional.empty());

            RemediationFindingsResult result = useCase.search(query().build());

            assertSoftly(softly -> {
                softly.assertThat(result.groups()).isEmpty();
                softly.assertThat(result.totals().total()).isZero();
                softly.assertThat(result.totalElements()).isZero();
                softly.assertThat(result.nonEligibleLegacyCount()).isZero();
            });
        }
    }

    @Nested
    @DisplayName("search() eligibility and identity tests")
    class EligibilityTests {

        @Test
        @DisplayName("Should_CollapseDuplicateOccurrences_When_SameValueDetectedTwiceOnSamePage")
        void Should_CollapseDuplicateOccurrences_When_SameValueDetectedTwiceOnSamePage() {
            RemediationFindingsResult result = useCase.search(query().build());

            assertThat(result.totalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should_CountLegacyDetections_When_FingerprintMissing")
        void Should_CountLegacyDetections_When_FingerprintMissing() {
            RemediationFindingsResult result = useCase.search(query().build());

            assertSoftly(softly -> {
                softly.assertThat(result.nonEligibleLegacyCount()).isEqualTo(1);
                softly.assertThat(allViews(result))
                        .extracting(RemediationFindingView::findingId)
                        .doesNotContainNull();
            });
        }

        @Test
        @DisplayName("Should_MarkAttachmentFindingsIneligible_When_FindingIsOnAttachment")
        void Should_MarkAttachmentFindingsIneligible_When_FindingIsOnAttachment() {
            RemediationFindingsResult result = useCase.search(query().build());

            RemediationFindingView attachmentView = viewById(result, avsAttachmentId);
            RemediationFindingView pageView = viewById(result, emailP1Id);
            assertSoftly(softly -> {
                softly.assertThat(attachmentView.eligibleForRedaction()).isFalse();
                softly.assertThat(attachmentView.ineligibilityReason())
                        .isEqualTo("ATTACHMENT_REDACTION_UNSUPPORTED");
                softly.assertThat(pageView.eligibleForRedaction()).isTrue();
                softly.assertThat(pageView.ineligibilityReason()).isNull();
            });
        }

        @Test
        @DisplayName("Should_ExposeSensitiveValueContextAndMaskedContext_When_BuildingViews")
        void Should_ExposeSensitiveValueContextAndMaskedContext_When_BuildingViews() {
            RemediationFindingsResult result = useCase.search(query().build());

            assertSoftly(softly -> {
                softly.assertThat(viewById(result, emailP1Id).maskedContext()).isEqualTo("ctx-email-1");
                softly.assertThat(viewById(result, emailP1Id).sensitiveValue()).isEqualTo("ENC:v1:opaque");
                softly.assertThat(viewById(result, emailP1Id).sensitiveContext()).isEqualTo("ENC:v1:context");
            });
        }
    }

    @Nested
    @DisplayName("search() grouping tests")
    class GroupingTests {

        @Test
        @DisplayName("Should_GroupByTypeSortedByKey_When_GroupByType")
        void Should_GroupByTypeSortedByKey_When_GroupByType() {
            RemediationFindingsResult result = useCase.search(query().build());

            assertSoftly(softly -> {
                softly.assertThat(result.groups())
                        .extracting(RemediationFindingGroup::key)
                        .containsExactly("AVS", "EMAIL", "IBAN", "PHONE");
                softly.assertThat(groupByKey(result, "EMAIL").label()).isEqualTo("Email Address");
                softly.assertThat(groupByKey(result, "EMAIL").total()).isEqualTo(2);
                softly.assertThat(groupByKey(result, "EMAIL").severity())
                        .isEqualTo(PersonallyIdentifiableInformationSeverity.MEDIUM);
            });
        }

        @Test
        @DisplayName("Should_GroupBySeverityHighestFirst_When_GroupBySeverity")
        void Should_GroupBySeverityHighestFirst_When_GroupBySeverity() {
            RemediationFindingsResult result = useCase.search(query().groupBy(GroupBy.SEVERITY).build());

            assertSoftly(softly -> {
                softly.assertThat(result.groups())
                        .extracting(RemediationFindingGroup::key)
                        .containsExactly("HIGH", "MEDIUM", "LOW");
                softly.assertThat(groupByKey(result, "HIGH").total()).isEqualTo(2);
                softly.assertThat(groupByKey(result, "MEDIUM").total()).isEqualTo(2);
                softly.assertThat(groupByKey(result, "LOW").total()).isEqualTo(1);
            });
        }
    }

    @Nested
    @DisplayName("search() selection resolution tests")
    class SelectionTests {

        @Test
        @DisplayName("Should_SelectAllPendingOfType_When_TypeChecked")
        void Should_SelectAllPendingOfType_When_TypeChecked() {
            RemediationFindingsResult result = useCase.search(
                    query().selection(selection().piiTypes(List.of("EMAIL")).build()).build());

            assertSoftly(softly -> {
                softly.assertThat(groupByKey(result, "EMAIL").selectedCount()).isEqualTo(2);
                softly.assertThat(groupByKey(result, "EMAIL").masterState()).isEqualTo(MasterState.ALL);
                softly.assertThat(groupByKey(result, "PHONE").selectedCount()).isZero();
                softly.assertThat(groupByKey(result, "PHONE").masterState()).isEqualTo(MasterState.NONE);
            });
        }

        @Test
        @DisplayName("Should_ComputePartialMasterState_When_OneFindingExcluded")
        void Should_ComputePartialMasterState_When_OneFindingExcluded() {
            RemediationFindingsResult result = useCase.search(query()
                    .selection(selection()
                            .piiTypes(List.of("EMAIL"))
                            .excludedFindingIds(Set.of(emailP1Id))
                            .build())
                    .build());

            assertSoftly(softly -> {
                softly.assertThat(groupByKey(result, "EMAIL").selectedCount()).isEqualTo(1);
                softly.assertThat(groupByKey(result, "EMAIL").masterState()).isEqualTo(MasterState.PARTIAL);
                softly.assertThat(viewById(result, emailP1Id).selected()).isFalse();
                softly.assertThat(viewById(result, emailP2Id).selected()).isTrue();
            });
        }

        @Test
        @DisplayName("Should_SelectSingleFinding_When_IncludedById")
        void Should_SelectSingleFinding_When_IncludedById() {
            RemediationFindingsResult result = useCase.search(query()
                    .selection(selection().includedFindingIds(Set.of(phoneP1Id)).build())
                    .build());

            assertSoftly(softly -> {
                softly.assertThat(groupByKey(result, "PHONE").selectedCount()).isEqualTo(1);
                softly.assertThat(groupByKey(result, "PHONE").masterState()).isEqualTo(MasterState.ALL);
                softly.assertThat(groupByKey(result, "EMAIL").masterState()).isEqualTo(MasterState.NONE);
            });
        }

        @Test
        @DisplayName("Should_SkipNonPendingFindings_When_TypeChecked")
        void Should_SkipNonPendingFindings_When_TypeChecked() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(row(emailP2Id, FindingRemediationStatus.FALSE_POSITIVE)));

            RemediationFindingsResult result = useCase.search(
                    query().selection(selection().piiTypes(List.of("EMAIL")).build()).build());

            assertSoftly(softly -> {
                softly.assertThat(groupByKey(result, "EMAIL").selectedCount()).isEqualTo(1);
                softly.assertThat(groupByKey(result, "EMAIL").masterState()).isEqualTo(MasterState.ALL);
                softly.assertThat(viewById(result, emailP2Id).selected()).isFalse();
                softly.assertThat(viewById(result, emailP2Id).status())
                        .isEqualTo(FindingRemediationStatus.FALSE_POSITIVE);
            });
        }
    }

    @Nested
    @DisplayName("search() filter tests")
    class FilterTests {

        @Test
        @DisplayName("Should_FilterByStatusFacetKeepingTotals_When_StatusFilterHandled")
        void Should_FilterByStatusFacetKeepingTotals_When_StatusFilterHandled() {
            when(findingRemediationStore.findByIds(anyCollection()))
                    .thenReturn(List.of(row(emailP1Id, FindingRemediationStatus.MANUALLY_HANDLED)));

            RemediationFindingsResult result = useCase.search(
                    query().statusFilter(StatusFilter.HANDLED).build());

            assertSoftly(softly -> {
                softly.assertThat(result.totalElements()).isEqualTo(1);
                softly.assertThat(allViews(result))
                        .extracting(RemediationFindingView::findingId)
                        .containsExactly(emailP1Id);
                softly.assertThat(result.totals().pending()).isEqualTo(4);
                softly.assertThat(result.totals().handled()).isEqualTo(1);
                softly.assertThat(result.totals().falsePositive()).isZero();
                softly.assertThat(result.totals().total()).isEqualTo(5);
            });
        }

        @Test
        @DisplayName("Should_ScopeToPageAndItsAttachments_When_PageIdProvided")
        void Should_ScopeToPageAndItsAttachments_When_PageIdProvided() {
            RemediationFindingsResult result = useCase.search(query().pageId("p2").build());

            assertSoftly(softly -> {
                softly.assertThat(result.totalElements()).isEqualTo(3);
                softly.assertThat(allViews(result))
                        .extracting(RemediationFindingView::findingId)
                        .containsExactlyInAnyOrder(emailP2Id, ibanP2Id, avsAttachmentId);
            });
        }

        @Test
        @DisplayName("Should_ScopeToSingleAttachment_When_AttachmentNameProvided")
        void Should_ScopeToSingleAttachment_When_AttachmentNameProvided() {
            RemediationFindingsResult result = useCase.search(
                    query().pageId("p2").attachmentName("doc.pdf").build());

            assertThat(allViews(result))
                    .extracting(RemediationFindingView::findingId)
                    .containsExactly(avsAttachmentId);
        }

        @Test
        @DisplayName("Should_FilterBySearchText_When_TextMatchesPageTitle")
        void Should_FilterBySearchText_When_TextMatchesPageTitle() {
            RemediationFindingsResult result = useCase.search(query().searchText("alpha").build());

            assertSoftly(softly -> {
                softly.assertThat(result.totalElements()).isEqualTo(2);
                softly.assertThat(allViews(result))
                        .extracting(RemediationFindingView::findingId)
                        .containsExactlyInAnyOrder(emailP1Id, phoneP1Id);
                softly.assertThat(result.totals().total()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("Should_FilterByItem_When_ItemFilterMatchesAttachment")
        void Should_FilterByItem_When_ItemFilterMatchesAttachment() {
            RemediationFindingsResult result = useCase.search(query().itemFilter("doc.pdf").build());

            assertThat(allViews(result))
                    .extracting(RemediationFindingView::findingId)
                    .containsExactly(avsAttachmentId);
        }
    }

    @Nested
    @DisplayName("search() pagination tests")
    class PaginationTests {

        @Test
        @DisplayName("Should_PaginateByWholeGroupsNeverSplitting_When_SecondPageRequested")
        void Should_PaginateByWholeGroupsNeverSplitting_When_SecondPageRequested() {
            RemediationFindingsResult result = useCase.search(query().page(1).pageSize(2).build());

            assertSoftly(softly -> {
                softly.assertThat(result.totalElements()).isEqualTo(5);
                softly.assertThat(result.totalGroups()).isEqualTo(4);
                softly.assertThat(result.page()).isEqualTo(1);
                softly.assertThat(result.pageSize()).isEqualTo(2);
                softly.assertThat(result.groups())
                        .extracting(RemediationFindingGroup::key)
                        .containsExactly("IBAN", "PHONE");
                softly.assertThat(allViews(result))
                        .extracting(RemediationFindingView::findingId)
                        .containsExactly(ibanP2Id, phoneP1Id);
                softly.assertThat(groupByKey(result, "IBAN").findings())
                        .hasSize((int) groupByKey(result, "IBAN").total());
            });
        }

        @Test
        @DisplayName("Should_ReturnAllGroupsWithEveryMember_When_PageSizeExceedsGroupCount")
        void Should_ReturnAllGroupsWithEveryMember_When_PageSizeExceedsGroupCount() {
            RemediationFindingsResult result = useCase.search(query().page(0).pageSize(20).build());

            assertThat(result.groups())
                    .isNotEmpty()
                    .allSatisfy(group -> assertThat(group.findings()).hasSize((int) group.total()));
        }

        @Test
        @DisplayName("Should_ReturnEmptyPage_When_PageBeyondLastGroup")
        void Should_ReturnEmptyPage_When_PageBeyondLastGroup() {
            RemediationFindingsResult result = useCase.search(query().page(9).pageSize(10).build());

            assertSoftly(softly -> {
                softly.assertThat(result.groups()).isEmpty();
                softly.assertThat(result.totalElements()).isEqualTo(5);
                softly.assertThat(result.totalGroups()).isEqualTo(4);
            });
        }
    }

    @Nested
    @DisplayName("search() occurrence-count tests")
    class OccurrenceCountTests {

        @Test
        @DisplayName("Should_CountCollapsedOccurrences_When_SameValueDetectedMultipleTimes")
        void Should_CountCollapsedOccurrences_When_SameValueDetectedMultipleTimes() {
            RemediationFindingsResult result = useCase.search(query().build());

            assertSoftly(softly -> {
                // EMAIL: fp-email-1 detected twice + fp-email-2 once = 2 values, 3 occurrences
                softly.assertThat(groupByKey(result, "EMAIL").total()).isEqualTo(2);
                softly.assertThat(groupByKey(result, "EMAIL").occurrenceCount()).isEqualTo(3);
                softly.assertThat(viewById(result, emailP1Id).occurrenceCount()).isEqualTo(2);
                softly.assertThat(viewById(result, emailP2Id).occurrenceCount()).isEqualTo(1);
                softly.assertThat(groupByKey(result, "PHONE").occurrenceCount()).isEqualTo(1);
            });
        }
    }

    private static FindingRemediation row(String findingId, FindingRemediationStatus status) {
        return FindingRemediation.builder()
                .findingId(findingId)
                .scanId(SCAN_ID)
                .spaceKey(SPACE)
                .pageId("p1")
                .piiType("EMAIL")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .detector("PRESIDIO")
                .status(status)
                .actor("tester")
                .occurredAt(Instant.parse("2026-07-06T10:00:00Z"))
                .build();
    }

    private static RemediationFindingsQuery.RemediationFindingsQueryBuilder query() {
        return RemediationFindingsQuery.builder()
                .spaceKey(SPACE)
                .groupBy(GroupBy.TYPE)
                .statusFilter(StatusFilter.ALL)
                .page(0)
                .pageSize(50)
                .selection(selection().build());
    }

    private static RemediationSelection.RemediationSelectionBuilder selection() {
        return RemediationSelection.builder().spaceKey(SPACE);
    }

    private static RemediationFindingGroup groupByKey(RemediationFindingsResult result, String key) {
        return result.groups().stream()
                .filter(group -> group.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static List<RemediationFindingView> allViews(RemediationFindingsResult result) {
        return result.groups().stream()
                .flatMap(group -> group.findings().stream())
                .toList();
    }

    private static RemediationFindingView viewById(RemediationFindingsResult result, String findingId) {
        return allViews(result).stream()
                .filter(view -> view.findingId().equals(findingId))
                .findFirst()
                .orElseThrow();
    }

    private static String findingId(String pageId, String attachmentName, String detector,
                                    String piiType, String fingerprint) {
        return FindingReference.builder()
                .spaceKey(SPACE)
                .pageId(pageId)
                .attachmentName(attachmentName)
                .detector(detector)
                .piiType(piiType)
                .severity(PersonallyIdentifiableInformationSeverity.HIGH)
                .valueFingerprint(fingerprint)
                .build()
                .findingId();
    }

    private static List<ConfluenceContentScanResult> defaultEvents() {
        return List.of(
                event("p1", "Alpha Page", null, List.of(
                        detection("EMAIL", "Email Address", "fp-email-1", 0.9, "ctx-email-1", DetectorSource.PRESIDIO),
                        detection("EMAIL", "Email Address", "fp-email-1", 0.7, "ctx-email-dup", DetectorSource.PRESIDIO),
                        detection("PHONE", "Phone Number", "fp-phone-1", 0.8, "ctx-phone-1", DetectorSource.REGEX),
                        detection("EMAIL", "Email Address", null, 0.6, "ctx-legacy", DetectorSource.PRESIDIO))),
                event("p2", "Beta Page", null, List.of(
                        detection("EMAIL", "Email Address", "fp-email-2", 0.95, "ctx-email-2", DetectorSource.MINISTRAL),
                        detection("IBAN", "IBAN", "fp-iban-1", 0.85, "ctx-iban-1", DetectorSource.REGEX))),
                event("p2", "Beta Page", "doc.pdf", List.of(
                        detection("AVS", "AVS Number", "fp-avs-1", 0.9, "ctx-avs-1", DetectorSource.MINISTRAL))));
    }

    private static ConfluenceContentScanResult event(String pageId, String pageTitle, String attachmentName,
                                                     List<DetectedPersonallyIdentifiableInformation> detections) {
        return ConfluenceContentScanResult.builder()
                .scanId(SCAN_ID)
                .spaceKey(SPACE)
                .eventType(attachmentName == null ? "item" : "attachmentItem")
                .pageId(pageId)
                .pageTitle(pageTitle)
                .attachmentName(attachmentName)
                .detectedPIIs(detections)
                .build();
    }

    private static DetectedPersonallyIdentifiableInformation detection(String piiType, String label,
                                                                       String fingerprint, double confidence,
                                                                       String maskedContext, DetectorSource source) {
        return DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(0)
                .endPosition(10)
                .piiType(piiType)
                .piiTypeLabel(label)
                .confidence(confidence)
                .sensitiveValue("ENC:v1:opaque")
                .sensitiveContext("ENC:v1:context")
                .maskedContext(maskedContext)
                .source(source)
                .valueFingerprint(fingerprint)
                .build();
    }
}
