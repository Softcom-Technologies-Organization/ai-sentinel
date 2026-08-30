package pro.softcom.aisentinel.domain.pii.remediation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("ObfuscationJob")
class ObfuscationJobTest {

    private static RemediationSelection selection() {
        return RemediationSelection.builder()
                .spaceKey("SPACE")
                .piiTypes(List.of("EMAIL_ADDRESS"))
                .build();
    }

    private static ObfuscationJob.ObfuscationJobBuilder job() {
        return ObfuscationJob.builder()
                .id("job-42")
                .spaceKey("SPACE")
                .status(ObfuscationJobStatus.RUNNING)
                .submittedSelection(selection())
                .resolvedFindingIds(List.of("finding-1", "finding-2"))
                .processed(1)
                .total(2)
                .outcomes(Map.of("finding-1",
                        FindingRedactionOutcome.of("EMAIL_ADDRESS", RedactionOutcome.REDACTED)))
                .actor("compliance-officer")
                .createdAt(Instant.parse("2026-07-05T10:00:00Z"))
                .updatedAt(Instant.parse("2026-07-05T10:05:00Z"));
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("Should_ExposeAllFields_When_BuiltWithFullData")
        void Should_ExposeAllFields_When_BuiltWithFullData() {
            ObfuscationJob obfuscationJob = job().build();

            assertSoftly(softly -> {
                softly.assertThat(obfuscationJob.id()).isEqualTo("job-42");
                softly.assertThat(obfuscationJob.spaceKey()).isEqualTo("SPACE");
                softly.assertThat(obfuscationJob.status()).isEqualTo(ObfuscationJobStatus.RUNNING);
                softly.assertThat(obfuscationJob.submittedSelection()).isEqualTo(selection());
                softly.assertThat(obfuscationJob.resolvedFindingIds()).containsExactly("finding-1", "finding-2");
                softly.assertThat(obfuscationJob.processed()).isEqualTo(1);
                softly.assertThat(obfuscationJob.total()).isEqualTo(2);
                softly.assertThat(obfuscationJob.outcomes())
                        .containsEntry("finding-1",
                                FindingRedactionOutcome.of("EMAIL_ADDRESS", RedactionOutcome.REDACTED));
                softly.assertThat(obfuscationJob.actor()).isEqualTo("compliance-officer");
                softly.assertThat(obfuscationJob.createdAt()).isEqualTo(Instant.parse("2026-07-05T10:00:00Z"));
                softly.assertThat(obfuscationJob.updatedAt()).isEqualTo(Instant.parse("2026-07-05T10:05:00Z"));
            });
        }

        @Test
        @DisplayName("Should_NormalizeNullCollectionsToEmpty_When_JobJustCreated")
        void Should_NormalizeNullCollectionsToEmpty_When_JobJustCreated() {
            ObfuscationJob obfuscationJob = job()
                    .resolvedFindingIds(null)
                    .outcomes(null)
                    .processed(0)
                    .total(0)
                    .build();

            assertSoftly(softly -> {
                softly.assertThat(obfuscationJob.resolvedFindingIds()).isEmpty();
                softly.assertThat(obfuscationJob.outcomes()).isEmpty();
            });
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_IdIsBlank")
        void Should_RejectConstruction_When_IdIsBlank() {
            var builder = job().id(" ");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_SpaceKeyIsBlank")
        void Should_RejectConstruction_When_SpaceKeyIsBlank() {
            var builder = job().spaceKey("");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("spaceKey");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_StatusIsMissing")
        void Should_RejectConstruction_When_StatusIsMissing() {
            var builder = job().status(null);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_SubmittedSelectionIsMissing")
        void Should_RejectConstruction_When_SubmittedSelectionIsMissing() {
            var builder = job().submittedSelection(null);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("submittedSelection");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_ProcessedIsNegative")
        void Should_RejectConstruction_When_ProcessedIsNegative() {
            var builder = job().processed(-1);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("processed");
        }

        @Test
        @DisplayName("Should_RejectConstruction_When_TotalIsNegative")
        void Should_RejectConstruction_When_TotalIsNegative() {
            var builder = job().total(-1);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("total");
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilder {

        @Test
        @DisplayName("Should_PreserveOtherFields_When_ProgressUpdated")
        void Should_PreserveOtherFields_When_ProgressUpdated() {
            ObfuscationJob updated = job().build().toBuilder()
                    .processed(2)
                    .status(ObfuscationJobStatus.COMPLETED)
                    .build();

            assertSoftly(softly -> {
                softly.assertThat(updated.processed()).isEqualTo(2);
                softly.assertThat(updated.status()).isEqualTo(ObfuscationJobStatus.COMPLETED);
                softly.assertThat(updated.id()).isEqualTo("job-42");
                softly.assertThat(updated.submittedSelection()).isEqualTo(selection());
            });
        }
    }

    @Nested
    @DisplayName("status lifecycle")
    class StatusLifecycle {

        @Test
        @DisplayName("Should_ExposeInterruptedStatus_When_RecoveringFromCrash")
        void Should_ExposeInterruptedStatus_When_RecoveringFromCrash() {
            assertThat(ObfuscationJobStatus.valueOf("INTERRUPTED"))
                    .isEqualTo(ObfuscationJobStatus.INTERRUPTED);
        }
    }
}
