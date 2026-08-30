package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import pro.softcom.aisentinel.domain.pii.remediation.FindingRedactionOutcome;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJob;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobStatus;
import pro.softcom.aisentinel.domain.pii.remediation.RedactionOutcome;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.ObfuscationJobRepository;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.out.jpa.entity.ObfuscationJobEntity;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaObfuscationJobAdapter")
class JpaObfuscationJobAdapterTest {

    private static final String JOB_ID = "job-42";
    private static final String SPACE_KEY = "SPACE";
    private static final Instant CREATED_AT = Instant.parse("2026-07-05T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-05T10:05:00Z");

    @Mock
    private ObfuscationJobRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JpaObfuscationJobAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaObfuscationJobAdapter(repository, objectMapper);
    }

    private static RemediationSelection selection() {
        return RemediationSelection.builder()
                .spaceKey(SPACE_KEY)
                .piiTypes(List.of("EMAIL_ADDRESS"))
                .excludedFindingIds(Set.of("excluded-1"))
                .build();
    }

    private static ObfuscationJob domainJob() {
        return ObfuscationJob.builder()
                .id(JOB_ID)
                .spaceKey(SPACE_KEY)
                .status(ObfuscationJobStatus.RUNNING)
                .submittedSelection(selection())
                .resolvedFindingIds(List.of("finding-1", "finding-2"))
                .processed(1)
                .total(2)
                .outcomes(Map.of("finding-1",
                        FindingRedactionOutcome.of("EMAIL_ADDRESS", RedactionOutcome.REDACTED)))
                .actor("compliance-officer")
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
    }

    private ObfuscationJobEntity entityOf(ObfuscationJob source) {
        return ObfuscationJobEntity.builder()
                .id(source.id())
                .spaceKey(source.spaceKey())
                .status(source.status().name())
                .submittedSelection(objectMapper.valueToTree(source.submittedSelection()))
                .resolvedFindingIds(objectMapper.valueToTree(source.resolvedFindingIds()))
                .processed(source.processed())
                .total(source.total())
                .outcomes(objectMapper.valueToTree(source.outcomes()))
                .actor(source.actor())
                .createdAt(source.createdAt())
                .updatedAt(source.updatedAt())
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Should_MapDomainToJsonbEntity_When_JobCreated")
        void Should_MapDomainToJsonbEntity_When_JobCreated() {
            adapter.create(domainJob());

            ArgumentCaptor<ObfuscationJobEntity> captor = ArgumentCaptor.forClass(ObfuscationJobEntity.class);
            verify(repository).saveAndFlush(captor.capture());
            ObfuscationJobEntity saved = captor.getValue();

            assertSoftly(softly -> {
                softly.assertThat(saved.getId()).isEqualTo(JOB_ID);
                softly.assertThat(saved.getSpaceKey()).isEqualTo(SPACE_KEY);
                softly.assertThat(saved.getStatus()).isEqualTo("RUNNING");
                softly.assertThat(saved.getSubmittedSelection().get("spaceKey").asText()).isEqualTo(SPACE_KEY);
                softly.assertThat(saved.getSubmittedSelection().get("piiTypes").get(0).asText())
                        .isEqualTo("EMAIL_ADDRESS");
                softly.assertThat(saved.getResolvedFindingIds().get(0).asText()).isEqualTo("finding-1");
                softly.assertThat(saved.getResolvedFindingIds().get(1).asText()).isEqualTo("finding-2");
                softly.assertThat(saved.getProcessed()).isEqualTo(1);
                softly.assertThat(saved.getTotal()).isEqualTo(2);
                softly.assertThat(saved.getOutcomes().get("finding-1").get("outcome").asText())
                        .isEqualTo("REDACTED");
                softly.assertThat(saved.getOutcomes().get("finding-1").get("piiType").asText())
                        .isEqualTo("EMAIL_ADDRESS");
                softly.assertThat(saved.getActor()).isEqualTo("compliance-officer");
                softly.assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
                softly.assertThat(saved.getUpdatedAt()).isEqualTo(UPDATED_AT);
            });
        }

        @Test
        @DisplayName("Should_TranslateConstraintViolation_When_RunningJobAlreadyExistsForSpace")
        void Should_TranslateConstraintViolation_When_RunningJobAlreadyExistsForSpace() {
            when(repository.saveAndFlush(any(ObfuscationJobEntity.class)))
                    .thenThrow(runningJobUniqueViolation());
            var job = domainJob();

            assertThatThrownBy(() -> adapter.create(job))
                    .isInstanceOf(ObfuscationJobAlreadyRunningException.class)
                    .hasMessageContaining(SPACE_KEY);
        }

        @Test
        @DisplayName("Should_RethrowOriginal_When_ViolationIsNotRunningJobUniqueIndex")
        void Should_RethrowOriginal_When_ViolationIsNotRunningJobUniqueIndex() {
            DataIntegrityViolationException other = new DataIntegrityViolationException(
                    "not-null violation",
                    new ConstraintViolationException("actor must not be null",
                            new SQLException("null value"), "pii_redaction_job_actor_not_null"));
            when(repository.saveAndFlush(any(ObfuscationJobEntity.class))).thenThrow(other);
            var job = domainJob();

            assertThatThrownBy(() -> adapter.create(job))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .isNotInstanceOf(ObfuscationJobAlreadyRunningException.class);
        }

        private static DataIntegrityViolationException runningJobUniqueViolation() {
            return new DataIntegrityViolationException("duplicate RUNNING job",
                    new ConstraintViolationException("duplicate key", new SQLException("duplicate"),
                            "uq_pii_redaction_job_running_per_space"));
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("Should_PersistProgressAndOutcomes_When_JobUpdated")
        void Should_PersistProgressAndOutcomes_When_JobUpdated() {
            ObfuscationJob updated = domainJob().toBuilder()
                    .processed(2)
                    .status(ObfuscationJobStatus.COMPLETED)
                    .outcomes(Map.of(
                            "finding-1", FindingRedactionOutcome.of("EMAIL_ADDRESS", RedactionOutcome.REDACTED),
                            "finding-2", FindingRedactionOutcome.of("EMAIL_ADDRESS",
                                    RedactionOutcome.SKIPPED_VALUE_NOT_FOUND)))
                    .build();

            adapter.update(updated);

            ArgumentCaptor<ObfuscationJobEntity> captor = ArgumentCaptor.forClass(ObfuscationJobEntity.class);
            verify(repository).save(captor.capture());
            ObfuscationJobEntity saved = captor.getValue();

            assertSoftly(softly -> {
                softly.assertThat(saved.getStatus()).isEqualTo("COMPLETED");
                softly.assertThat(saved.getProcessed()).isEqualTo(2);
                softly.assertThat(saved.getOutcomes().get("finding-2").get("outcome").asText())
                        .isEqualTo("SKIPPED_VALUE_NOT_FOUND");
            });
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Should_RoundTripJobThroughJsonb_When_JobExists")
        void Should_RoundTripJobThroughJsonb_When_JobExists() {
            ObfuscationJob original = domainJob();
            when(repository.findById(JOB_ID)).thenReturn(Optional.of(entityOf(original)));

            Optional<ObfuscationJob> result = adapter.findById(JOB_ID);

            assertThat(result).contains(original);
        }

        @Test
        @DisplayName("Should_ReturnEmpty_When_JobUnknown")
        void Should_ReturnEmpty_When_JobUnknown() {
            when(repository.findById("unknown")).thenReturn(Optional.empty());

            assertThat(adapter.findById("unknown")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveBySpace")
    class FindActiveBySpace {

        @Test
        @DisplayName("Should_QueryRunningStatus_When_LookingForActiveJob")
        void Should_QueryRunningStatus_When_LookingForActiveJob() {
            when(repository.findFirstBySpaceKeyAndStatus(SPACE_KEY, "RUNNING"))
                    .thenReturn(Optional.of(entityOf(domainJob())));

            Optional<ObfuscationJob> result = adapter.findActiveBySpace(SPACE_KEY);

            assertThat(result).contains(domainJob());
        }

        @Test
        @DisplayName("Should_ReturnEmpty_When_NoRunningJobInSpace")
        void Should_ReturnEmpty_When_NoRunningJobInSpace() {
            when(repository.findFirstBySpaceKeyAndStatus(SPACE_KEY, "RUNNING"))
                    .thenReturn(Optional.empty());

            assertThat(adapter.findActiveBySpace(SPACE_KEY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("markInterruptedOnBoot")
    class MarkInterruptedOnBoot {

        @Test
        @DisplayName("Should_ReturnInterruptedCount_When_RunningJobsRecovered")
        void Should_ReturnInterruptedCount_When_RunningJobsRecovered() {
            when(repository.markRunningAsInterrupted(any(Instant.class))).thenReturn(3);

            assertThat(adapter.markInterruptedOnBoot()).isEqualTo(3);
        }
    }
}
