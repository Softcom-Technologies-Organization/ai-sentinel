package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediation;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.FindingRemediationRepository;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.entity.FindingRemediationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaFindingRemediationAdapter")
class JpaFindingRemediationAdapterTest {

    private static final String FINDING_ID = "a".repeat(64);
    private static final String SPACE_KEY = "SPACE";
    private static final String PAGE_ID = "12345";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-05T10:00:00Z");

    @Mock
    private FindingRemediationRepository repository;

    @Captor
    private ArgumentCaptor<List<FindingRemediationEntity>> savedEntitiesCaptor;

    @InjectMocks
    private JpaFindingRemediationAdapter adapter;

    private static FindingRemediationEntity entity() {
        return FindingRemediationEntity.builder()
                .findingId(FINDING_ID)
                .scanId("scan-123")
                .spaceKey(SPACE_KEY)
                .pageId(PAGE_ID)
                .attachmentName("report.pdf")
                .piiType("EMAIL_ADDRESS")
                .severity("MEDIUM")
                .detector("MINISTRAL")
                .status("FALSE_POSITIVE")
                .statusReason("unchecked by operator")
                .actor("compliance-officer")
                .occurredAt(OCCURRED_AT)
                .redactionJobId("job-42")
                .build();
    }

    private static FindingRemediation domainRow() {
        return FindingRemediation.builder()
                .findingId(FINDING_ID)
                .scanId("scan-123")
                .spaceKey(SPACE_KEY)
                .pageId(PAGE_ID)
                .attachmentName("report.pdf")
                .piiType("EMAIL_ADDRESS")
                .severity(PersonallyIdentifiableInformationSeverity.MEDIUM)
                .detector("MINISTRAL")
                .status(FindingRemediationStatus.FALSE_POSITIVE)
                .statusReason("unchecked by operator")
                .actor("compliance-officer")
                .occurredAt(OCCURRED_AT)
                .redactionJobId("job-42")
                .build();
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {

        @Test
        @DisplayName("Should_MapEntityToDomain_When_FindingExists")
        void Should_MapEntityToDomain_When_FindingExists() {
            when(repository.findAllById(List.of(FINDING_ID))).thenReturn(List.of(entity()));

            List<FindingRemediation> result = adapter.findByIds(List.of(FINDING_ID));

            assertThat(result).containsExactly(domainRow());
        }

        @Test
        @DisplayName("Should_ReturnEmptyList_When_NoIdsProvided")
        void Should_ReturnEmptyList_When_NoIdsProvided() {
            assertSoftly(softly -> {
                softly.assertThat(adapter.findByIds(List.of())).isEmpty();
                softly.assertThat(adapter.findByIds(null)).isEmpty();
            });
            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("findBySpace")
    class FindBySpace {

        @Test
        @DisplayName("Should_QueryWithoutStatusFilter_When_StatusesEmpty")
        void Should_QueryWithoutStatusFilter_When_StatusesEmpty() {
            when(repository.findBySpaceKey(SPACE_KEY)).thenReturn(List.of(entity()));

            List<FindingRemediation> result = adapter.findBySpace(SPACE_KEY, Set.of());

            assertThat(result).containsExactly(domainRow());
        }

        @Test
        @DisplayName("Should_FilterByStatusNames_When_StatusesProvided")
        void Should_FilterByStatusNames_When_StatusesProvided() {
            when(repository.findBySpaceKeyAndStatusIn(SPACE_KEY, Set.of("REDACTED")))
                    .thenReturn(List.of());

            List<FindingRemediation> result =
                    adapter.findBySpace(SPACE_KEY, Set.of(FindingRemediationStatus.REDACTED));

            assertThat(result).isEmpty();
            verify(repository).findBySpaceKeyAndStatusIn(SPACE_KEY, Set.of("REDACTED"));
        }
    }

    @Nested
    @DisplayName("findByPage")
    class FindByPage {

        @Test
        @DisplayName("Should_QueryWithoutStatusFilter_When_StatusesNull")
        void Should_QueryWithoutStatusFilter_When_StatusesNull() {
            when(repository.findBySpaceKeyAndPageId(SPACE_KEY, PAGE_ID)).thenReturn(List.of(entity()));

            List<FindingRemediation> result = adapter.findByPage(SPACE_KEY, PAGE_ID, null);

            assertThat(result).containsExactly(domainRow());
        }

        @Test
        @DisplayName("Should_FilterByStatusNames_When_StatusesProvided")
        void Should_FilterByStatusNames_When_StatusesProvided() {
            when(repository.findBySpaceKeyAndPageIdAndStatusIn(SPACE_KEY, PAGE_ID, Set.of("PENDING")))
                    .thenReturn(List.of());

            List<FindingRemediation> result =
                    adapter.findByPage(SPACE_KEY, PAGE_ID, Set.of(FindingRemediationStatus.PENDING));

            assertThat(result).isEmpty();
            verify(repository).findBySpaceKeyAndPageIdAndStatusIn(SPACE_KEY, PAGE_ID, Set.of("PENDING"));
        }
    }

    @Nested
    @DisplayName("upsertAll")
    class UpsertAll {

        @Test
        @DisplayName("Should_MapDomainToEntity_When_TransitionRowsSaved")
        void Should_MapDomainToEntity_When_TransitionRowsSaved() {
            adapter.upsertAll(List.of(domainRow()));

            verify(repository).saveAll(savedEntitiesCaptor.capture());
            FindingRemediationEntity saved = savedEntitiesCaptor.getValue().getFirst();

            assertSoftly(softly -> {
                softly.assertThat(saved.getFindingId()).isEqualTo(FINDING_ID);
                softly.assertThat(saved.getScanId()).isEqualTo("scan-123");
                softly.assertThat(saved.getSpaceKey()).isEqualTo(SPACE_KEY);
                softly.assertThat(saved.getPageId()).isEqualTo(PAGE_ID);
                softly.assertThat(saved.getAttachmentName()).isEqualTo("report.pdf");
                softly.assertThat(saved.getPiiType()).isEqualTo("EMAIL_ADDRESS");
                softly.assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
                softly.assertThat(saved.getDetector()).isEqualTo("MINISTRAL");
                softly.assertThat(saved.getStatus()).isEqualTo("FALSE_POSITIVE");
                softly.assertThat(saved.getStatusReason()).isEqualTo("unchecked by operator");
                softly.assertThat(saved.getActor()).isEqualTo("compliance-officer");
                softly.assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
                softly.assertThat(saved.getRedactionJobId()).isEqualTo("job-42");
            });
        }

        @Test
        @DisplayName("Should_NotTouchRepository_When_NoRowsProvided")
        void Should_NotTouchRepository_When_NoRowsProvided() {
            adapter.upsertAll(List.of());
            adapter.upsertAll(null);

            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("findStatusesByIds")
    class FindStatusesByIds {

        @Test
        @DisplayName("Should_ReturnStatusByFindingId_When_RowsExist")
        void Should_ReturnStatusByFindingId_When_RowsExist() {
            when(repository.findAllById(List.of(FINDING_ID))).thenReturn(List.of(entity()));

            Map<String, FindingRemediationStatus> result = adapter.findStatusesByIds(List.of(FINDING_ID));

            assertThat(result).containsExactly(Map.entry(FINDING_ID, FindingRemediationStatus.FALSE_POSITIVE));
        }

        @Test
        @DisplayName("Should_ReturnEmptyMap_When_NoIdsProvided")
        void Should_ReturnEmptyMap_When_NoIdsProvided() {
            assertSoftly(softly -> {
                softly.assertThat(adapter.findStatusesByIds(List.of())).isEmpty();
                softly.assertThat(adapter.findStatusesByIds(null)).isEmpty();
            });
            verifyNoInteractions(repository);
        }
    }
}
