package pro.softcom.aisentinel.domain.pii.remediation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus.FALSE_POSITIVE;
import static pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus.MANUALLY_HANDLED;
import static pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus.PENDING;
import static pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus.PROPOSED_FALSE_POSITIVE;
import static pro.softcom.aisentinel.domain.pii.remediation.FindingRemediationStatus.REDACTED;

@DisplayName("FindingRemediationStatus")
class FindingRemediationStatusTest {

    @Nested
    @DisplayName("transitionTo")
    class TransitionTo {

        @Test
        @DisplayName("Should_AllowRedaction_When_StatusIsPending")
        void Should_AllowRedaction_When_StatusIsPending() {
            assertThat(PENDING.transitionTo(REDACTED)).isEqualTo(REDACTED);
        }

        @Test
        @DisplayName("Should_AllowManualHandling_When_StatusIsPending")
        void Should_AllowManualHandling_When_StatusIsPending() {
            assertThat(PENDING.transitionTo(MANUALLY_HANDLED)).isEqualTo(MANUALLY_HANDLED);
        }

        @Test
        @DisplayName("Should_AllowFalsePositive_When_StatusIsPending")
        void Should_AllowFalsePositive_When_StatusIsPending() {
            assertThat(PENDING.transitionTo(FALSE_POSITIVE)).isEqualTo(FALSE_POSITIVE);
        }

        @Test
        @DisplayName("Should_RestoreToPending_When_StatusIsManuallyHandled")
        void Should_RestoreToPending_When_StatusIsManuallyHandled() {
            assertThat(MANUALLY_HANDLED.transitionTo(PENDING)).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("Should_RestoreToPending_When_StatusIsFalsePositive")
        void Should_RestoreToPending_When_StatusIsFalsePositive() {
            assertThat(FALSE_POSITIVE.transitionTo(PENDING)).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("Should_RejectAnyTransition_When_StatusIsRedacted")
        void Should_RejectAnyTransition_When_StatusIsRedacted() {
            assertSoftly(softly -> {
                for (FindingRemediationStatus target : FindingRemediationStatus.values()) {
                    softly.assertThatThrownBy(() -> REDACTED.transitionTo(target))
                            .as("REDACTED is terminal, transition to %s must fail", target)
                            .isInstanceOf(IllegalStatusTransitionException.class);
                }
            });
        }

        @Test
        @DisplayName("Should_RejectSelfTransition_When_TargetEqualsCurrentStatus")
        void Should_RejectSelfTransition_When_TargetEqualsCurrentStatus() {
            assertSoftly(softly -> {
                for (FindingRemediationStatus status : FindingRemediationStatus.values()) {
                    softly.assertThatThrownBy(() -> status.transitionTo(status))
                            .as("Self transition on %s must fail", status)
                            .isInstanceOf(IllegalStatusTransitionException.class);
                }
            });
        }

        @Test
        @DisplayName("Should_RejectDirectRedaction_When_StatusIsManuallyHandled")
        void Should_RejectDirectRedaction_When_StatusIsManuallyHandled() {
            assertThatThrownBy(() -> MANUALLY_HANDLED.transitionTo(REDACTED))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should_RejectDirectRedaction_When_StatusIsFalsePositive")
        void Should_RejectDirectRedaction_When_StatusIsFalsePositive() {
            assertThatThrownBy(() -> FALSE_POSITIVE.transitionTo(REDACTED))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should_RejectCrossHandlingTransition_When_StatusIsManuallyHandled")
        void Should_RejectCrossHandlingTransition_When_StatusIsManuallyHandled() {
            assertThatThrownBy(() -> MANUALLY_HANDLED.transitionTo(FALSE_POSITIVE))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should_ExposeFromAndTargetStatuses_When_TransitionIsRejected")
        void Should_ExposeFromAndTargetStatuses_When_TransitionIsRejected() {
            IllegalStatusTransitionException exception = catchThrowableOfType(
                    IllegalStatusTransitionException.class,
                    () -> REDACTED.transitionTo(PENDING));

            assertSoftly(softly -> {
                softly.assertThat(exception.getFromStatus()).isEqualTo(REDACTED);
                softly.assertThat(exception.getToStatus()).isEqualTo(PENDING);
                softly.assertThat(exception.getMessage()).contains("REDACTED").contains("PENDING");
            });
        }
    }

    @Nested
    @DisplayName("canTransitionTo")
    class CanTransitionTo {

        @Test
        @DisplayName("Should_ReturnTrue_When_TransitionIsAllowed")
        void Should_ReturnTrue_When_TransitionIsAllowed() {
            assertSoftly(softly -> {
                softly.assertThat(PENDING.canTransitionTo(REDACTED)).isTrue();
                softly.assertThat(PENDING.canTransitionTo(MANUALLY_HANDLED)).isTrue();
                softly.assertThat(PENDING.canTransitionTo(FALSE_POSITIVE)).isTrue();
                softly.assertThat(MANUALLY_HANDLED.canTransitionTo(PENDING)).isTrue();
                softly.assertThat(FALSE_POSITIVE.canTransitionTo(PENDING)).isTrue();
            });
        }

        @Test
        @DisplayName("Should_ReturnFalse_When_TransitionIsForbidden")
        void Should_ReturnFalse_When_TransitionIsForbidden() {
            assertSoftly(softly -> {
                for (FindingRemediationStatus target : FindingRemediationStatus.values()) {
                    softly.assertThat(REDACTED.canTransitionTo(target))
                            .as("REDACTED -> %s", target)
                            .isFalse();
                }
                softly.assertThat(MANUALLY_HANDLED.canTransitionTo(FALSE_POSITIVE)).isFalse();
                softly.assertThat(FALSE_POSITIVE.canTransitionTo(MANUALLY_HANDLED)).isFalse();
                softly.assertThat(PENDING.canTransitionTo(PENDING)).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("proposed false positive (LLM judge)")
    class ProposedFalsePositive {

        @Test
        @DisplayName("Should_AllowProposal_When_StatusIsPending")
        void Should_AllowProposal_When_StatusIsPending() {
            assertThat(PENDING.transitionTo(PROPOSED_FALSE_POSITIVE)).isEqualTo(PROPOSED_FALSE_POSITIVE);
        }

        @Test
        @DisplayName("Should_AllowConfirmOrKeep_When_StatusIsProposed")
        void Should_AllowConfirmOrKeep_When_StatusIsProposed() {
            assertSoftly(softly -> {
                softly.assertThat(PROPOSED_FALSE_POSITIVE.canTransitionTo(FALSE_POSITIVE)).isTrue();
                softly.assertThat(PROPOSED_FALSE_POSITIVE.canTransitionTo(PENDING)).isTrue();
                softly.assertThat(PROPOSED_FALSE_POSITIVE.canTransitionTo(REDACTED)).isFalse();
                softly.assertThat(PROPOSED_FALSE_POSITIVE.canTransitionTo(MANUALLY_HANDLED)).isFalse();
            });
        }

        @Test
        @DisplayName("Should_RejectProposal_When_StatusIsNotPending")
        void Should_RejectProposal_When_StatusIsNotPending() {
            assertSoftly(softly -> {
                softly.assertThat(FALSE_POSITIVE.canTransitionTo(PROPOSED_FALSE_POSITIVE)).isFalse();
                softly.assertThat(MANUALLY_HANDLED.canTransitionTo(PROPOSED_FALSE_POSITIVE)).isFalse();
            });
        }
    }
}
