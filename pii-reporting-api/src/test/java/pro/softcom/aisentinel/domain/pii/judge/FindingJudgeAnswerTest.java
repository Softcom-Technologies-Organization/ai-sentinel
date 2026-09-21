package pro.softcom.aisentinel.domain.pii.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindingJudgeAnswer")
class FindingJudgeAnswerTest {

    @Test
    @DisplayName("Should_HonourRejection_When_ConfidenceReachesThreshold")
    void Should_HonourRejection_When_ConfidenceReachesThreshold() {
        FindingJudgeAnswer answer = new FindingJudgeAnswer("FALSE_POSITIVE", 0.8, "motif (b)", "");

        assertThat(answer.verdictHonoring(0.8)).contains(JudgeVerdict.FALSE_POSITIVE);
    }

    @Test
    @DisplayName("Should_DowngradeToUnsure_When_RejectionBelowThreshold")
    void Should_DowngradeToUnsure_When_RejectionBelowThreshold() {
        FindingJudgeAnswer answer = new FindingJudgeAnswer("false_positive", 0.79, "motif (b)", "");

        assertThat(answer.verdictHonoring(0.8)).contains(JudgeVerdict.UNSURE);
    }

    @Test
    @DisplayName("Should_KeepTruePositive_When_ConfidenceLow")
    void Should_KeepTruePositive_When_ConfidenceLow() {
        FindingJudgeAnswer answer = new FindingJudgeAnswer("TRUE_POSITIVE", 0.2, "ok", "");

        assertThat(answer.verdictHonoring(0.8)).contains(JudgeVerdict.TRUE_POSITIVE);
    }

    @Test
    @DisplayName("Should_YieldNoVerdict_When_UnparseableOrFailed")
    void Should_YieldNoVerdict_When_UnparseableOrFailed() {
        assertThat(new FindingJudgeAnswer("UNPARSEABLE", 0.0, "garbage", "").verdictHonoring(0.8)).isEmpty();
        assertThat(new FindingJudgeAnswer("TRUE_POSITIVE", 0.9, "", "ConnectError").verdictHonoring(0.8)).isEmpty();
    }
}
