package pro.softcom.aisentinel.domain.pii.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.ScanStatus;

class ScanCheckpointStatusTransitionTest {

    @Test
    @DisplayName("Should_AllowTransition_When_TargetStatusIsSameAsCurrent")
    void Should_AllowTransition_When_TargetStatusIsSameAsCurrent() {
        SoftAssertions softly = new SoftAssertions();

        for (ScanStatus status : ScanStatus.values()) {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(status, Initiator.USER);

            softly.assertThat(underTest.isTransitionAllowed(status))
                    .as("Idempotent transition should be allowed for %s", status)
                    .isTrue();

            softly.assertThat(underTest.transition(status))
                    .as("Idempotent transition should return same status for %s", status)
                    .isEqualTo(status);
        }

        softly.assertAll();
    }

    @Nested
    @DisplayName("FAILED status transitions")
    class FailedStatusTransitions {

        @Test
        @DisplayName("Should_AllowRestartToRunning_When_FailedInitiatedByUser")
        void Should_AllowRestartToRunning_When_FailedInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.FAILED, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.RUNNING)).isTrue();
            assertThat(underTest.transition(ScanStatus.RUNNING)).isEqualTo(ScanStatus.RUNNING);
        }

        @Test
        @DisplayName("Should_RejectRestartToRunning_When_FailedInitiatedBySystem")
        void Should_RejectRestartToRunning_When_FailedInitiatedBySystem() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.FAILED, Initiator.SYSTEM);

            assertThat(underTest.isTransitionAllowed(ScanStatus.RUNNING)).isFalse();

            assertThatThrownBy(() -> underTest.transition(ScanStatus.RUNNING))
                    .isInstanceOf(IllegalScanStatusTransitionException.class)
                    .hasMessageContaining("FAILED")
                    .hasMessageContaining("RUNNING")
                    .hasMessageContaining("SYSTEM");
        }
    }

    @Nested
    @DisplayName("RUNNING status transitions")
    class RunningStatusTransitions {

        @Test
        @DisplayName("Should_AllowPause_When_RunningInitiatedByUser")
        void Should_AllowPause_When_RunningInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.RUNNING, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.PAUSED)).isTrue();
            assertThat(underTest.transition(ScanStatus.PAUSED)).isEqualTo(ScanStatus.PAUSED);
        }

        @Test
        @DisplayName("Should_RejectSystemCompletion_When_RunningInitiatedByUser")
        void Should_RejectSystemCompletion_When_RunningInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.RUNNING, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.COMPLETED)).isFalse();

            assertThatThrownBy(() -> underTest.transition(ScanStatus.COMPLETED))
                    .isInstanceOf(IllegalScanStatusTransitionException.class)
                    .hasMessageContaining("RUNNING")
                    .hasMessageContaining("COMPLETED")
                    .hasMessageContaining("USER");
        }

        @Test
        @DisplayName("Should_AllowCompletionAndFailure_When_RunningInitiatedBySystem")
        void Should_AllowCompletionAndFailure_When_RunningInitiatedBySystem() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.RUNNING, Initiator.SYSTEM);

            SoftAssertions softly = new SoftAssertions();

            softly.assertThat(underTest.isTransitionAllowed(ScanStatus.COMPLETED)).isTrue();
            softly.assertThat(underTest.transition(ScanStatus.COMPLETED))
                    .isEqualTo(ScanStatus.COMPLETED);

            ScanCheckpointStatusTransition failureTransition =
                    new ScanCheckpointStatusTransition(ScanStatus.RUNNING, Initiator.SYSTEM);

            softly.assertThat(failureTransition.isTransitionAllowed(ScanStatus.FAILED)).isTrue();
            softly.assertThat(failureTransition.transition(ScanStatus.FAILED))
                    .isEqualTo(ScanStatus.FAILED);

            softly.assertAll();
        }
    }

    @Nested
    @DisplayName("PAUSED status transitions")
    class PausedStatusTransitions {

        @Test
        @DisplayName("Should_AllowResumeToRunning_When_PausedInitiatedByUser")
        void Should_AllowResumeToRunning_When_PausedInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.PAUSED, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.RUNNING)).isTrue();
            assertThat(underTest.transition(ScanStatus.RUNNING)).isEqualTo(ScanStatus.RUNNING);
        }

        @Test
        @DisplayName("Should_RejectCompletion_When_PausedInitiatedByUser")
        void Should_RejectCompletion_When_PausedInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.PAUSED, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.COMPLETED)).isFalse();

            assertThatThrownBy(() -> underTest.transition(ScanStatus.COMPLETED))
                    .isInstanceOf(IllegalScanStatusTransitionException.class)
                    .hasMessageContaining("PAUSED")
                    .hasMessageContaining("COMPLETED")
                    .hasMessageContaining("USER");
        }

        @Test
        @DisplayName("Should_AllowCompletion_When_PausedInitiatedBySystem")
        void Should_AllowCompletion_When_PausedInitiatedBySystem() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.PAUSED, Initiator.SYSTEM);

            assertThat(underTest.isTransitionAllowed(ScanStatus.COMPLETED)).isTrue();
            assertThat(underTest.transition(ScanStatus.COMPLETED))
                    .isEqualTo(ScanStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("NOT_STARTED status transitions")
    class NotStartedStatusTransitions {

        @Test
        @DisplayName("Should_AllowStartToRunning_When_NotStartedInitiatedBySystem")
        void Should_AllowStartToRunning_When_NotStartedInitiatedBySystem() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.NOT_STARTED, Initiator.SYSTEM);

            assertThat(underTest.isTransitionAllowed(ScanStatus.RUNNING)).isTrue();
            assertThat(underTest.transition(ScanStatus.RUNNING)).isEqualTo(ScanStatus.RUNNING);
        }

        @Test
        @DisplayName("Should_RejectStartToRunning_When_NotStartedInitiatedByUser")
        void Should_RejectStartToRunning_When_NotStartedInitiatedByUser() {
            ScanCheckpointStatusTransition underTest =
                    new ScanCheckpointStatusTransition(ScanStatus.NOT_STARTED, Initiator.USER);

            assertThat(underTest.isTransitionAllowed(ScanStatus.RUNNING)).isFalse();

            assertThatThrownBy(() -> underTest.transition(ScanStatus.RUNNING))
                    .isInstanceOf(IllegalScanStatusTransitionException.class)
                    .hasMessageContaining("NOT_STARTED")
                    .hasMessageContaining("RUNNING")
                    .hasMessageContaining("USER");
        }
    }

    @Nested
    @DisplayName("COMPLETED status transitions")
    class CompletedStatusTransitions {

        @Test
        @DisplayName("Should_RejectAnyChange_When_CompletedRegardlessOfInitiator")
        void Should_RejectAnyChange_When_CompletedRegardlessOfInitiator() {
            SoftAssertions softly = new SoftAssertions();

            for (Initiator initiator : Initiator.values()) {
                ScanCheckpointStatusTransition underTest =
                        new ScanCheckpointStatusTransition(ScanStatus.COMPLETED, initiator);

                for (ScanStatus target : ScanStatus.values()) {
                    if (target == ScanStatus.COMPLETED) {
                        // cas idempotent déjà couvert dans un autre test
                        continue;
                    }

                    softly.assertThat(underTest.isTransitionAllowed(target))
                            .as("COMPLETED -> %s should be rejected for initiator %s", target, initiator)
                            .isFalse();

                    softly.assertThatThrownBy(() -> underTest.transition(target))
                            .as("COMPLETED -> %s should throw for initiator %s", target, initiator)
                            .isInstanceOf(IllegalScanStatusTransitionException.class);
                }
            }

            softly.assertAll();
        }
    }
}
