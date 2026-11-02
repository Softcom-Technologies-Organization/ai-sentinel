package pro.softcom.aisentinel.domain.pii.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeEndpointTest {

    @Test
    void Should_UseJudgeEndpoint_When_Configured() {
        assertThat(new JudgeEndpoint("strata", 8081).resolve("ministral", 1234))
                .isEqualTo(new JudgeEndpoint("strata", 8081));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void Should_FallBackToDetectionEndpoint_When_NoOverride(String host) {
        assertThat(new JudgeEndpoint(host, null).resolve("ministral", 1234))
                .isEqualTo(new JudgeEndpoint("ministral", 1234));
    }

    @Test
    void Should_FallBackOnlyMissingValue_When_OverrideIsPartial() {
        assertThat(new JudgeEndpoint("strata", null).resolve("ministral", 1234))
                .isEqualTo(new JudgeEndpoint("strata", 1234));
        assertThat(new JudgeEndpoint(null, 8081).resolve("ministral", 1234))
                .isEqualTo(new JudgeEndpoint("ministral", 8081));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65536})
    void Should_RejectEndpoint_When_PortIsInvalid(int port) {
        assertThatThrownBy(() -> new JudgeEndpoint("strata", port))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 65535})
    void Should_AcceptEndpoint_When_PortIsAtBoundary(int port) {
        assertThat(new JudgeEndpoint("strata", port).port()).isEqualTo(port);
    }
}
