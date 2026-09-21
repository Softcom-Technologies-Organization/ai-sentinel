package pro.softcom.aisentinel.application.pii.judge.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JudgeContextWindow")
class JudgeContextWindowTest {

    @Test
    @DisplayName("Should_KeepWholeLine_When_ShorterThanWindow")
    void Should_KeepWholeLine_When_ShorterThanWindow() {
        assertThat(JudgeContextWindow.around("Compte : CH93 0076.", "CH93 0076")).isEqualTo("Compte : CH93 0076.");
    }

    @Test
    @DisplayName("Should_CenterOnValue_When_LineIsLong")
    void Should_CenterOnValue_When_LineIsLong() {
        String line = "a".repeat(1000) + "SECRET" + "b".repeat(1000);

        String window = JudgeContextWindow.around(line, "SECRET");

        assertThat(window).hasSize(2 * JudgeContextWindow.SIDE_CHARS + "SECRET".length())
                .startsWith("a").endsWith("b").contains("SECRET");
    }

    @Test
    @DisplayName("Should_TruncateHead_When_ValueAbsentFromLine")
    void Should_TruncateHead_When_ValueAbsentFromLine() {
        String window = JudgeContextWindow.around("x".repeat(2000), "missing");

        assertThat(window).hasSize(2 * JudgeContextWindow.SIDE_CHARS);
    }

    @Test
    @DisplayName("Should_FallBackToValue_When_NoContext")
    void Should_FallBackToValue_When_NoContext() {
        assertThat(JudgeContextWindow.around(null, "v")).isEqualTo("v");
        assertThat(JudgeContextWindow.around(null, null)).isEmpty();
    }
}
