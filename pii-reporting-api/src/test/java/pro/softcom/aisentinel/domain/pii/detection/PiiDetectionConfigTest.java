package pro.softcom.aisentinel.domain.pii.detection;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PiiDetectionConfig domain model.
 */
class PiiDetectionConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"LM_STUDIO", "OMLX", "STRATA"})
    void Should_PreferSavedJudgeEndpoint_When_EnvironmentAlsoConfigured(String provider) {
        PiiDetectionConfig config = judgeConfig(provider, " omlx ", 8000);

        assertThat(config.judgeProvider()).isEqualTo(provider);
        assertThat(config.judgeHost()).isEqualTo("omlx");
        assertThat(config.judgeEndpoint(new JudgeEndpoint("environment", 8080)))
                .isEqualTo(new JudgeEndpoint("omlx", 8000));
        assertThat(config.lmStudioJudgeModel()).isEqualTo("manual-model");
    }

    @Test
    void Should_KeepLegacyFieldsNull_When_JudgeIsNotConfigured() {
        PiiDetectionConfig config = judgeConfig(null, null, null);

        assertThat(config.judgeProvider()).isNull();
        assertThat(config.judgeHost()).isNull();
        assertThat(config.judgePort()).isNull();
        assertThat(config.judgeEndpoint(new JudgeEndpoint("environment", 8080)))
                .isEqualTo(new JudgeEndpoint("environment", 8080));
        assertThat(config.judgeEndpoint(new JudgeEndpoint(null, null)))
                .isEqualTo(new JudgeEndpoint("localhost", 1234));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void Should_RejectProvider_When_JudgeHostIsMissing(String host) {
        assertThatThrownBy(() -> judgeConfig("OMLX", host, 8000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_RejectProvider_When_JudgePortIsMissing() {
        assertThatThrownBy(() -> judgeConfig("OMLX", "localhost", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "omlx", ""})
    void Should_RejectJudgeProvider_When_Unsupported(String provider) {
        assertThatThrownBy(() -> judgeConfig(provider, "localhost", 8000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PiiDetectionConfig judgeConfig(String provider, String host, Integer port) {
        return new PiiDetectionConfig(1, true, true, false, 2048, 410,
                new BigDecimal("0.30"), false, "localhost", 1234, null, "manual-model",
                1, false, null, LocalDateTime.now(), "test", false, true, true, true,
                provider, host, port, null);
    }

    @Test
    void Should_ResolveEachFieldIndependently_When_LegacyOverridesArePartial() {
        JudgeEndpoint fallback = new JudgeEndpoint("environment", 8080);

        assertThat(judgeConfig(null, "saved", null).judgeEndpoint(fallback))
                .isEqualTo(new JudgeEndpoint("saved", 8080));
        assertThat(judgeConfig(null, null, 8000).judgeEndpoint(fallback))
                .isEqualTo(new JudgeEndpoint("environment", 8000));
        assertThat(judgeConfig(null, null, null).judgeEndpoint(new JudgeEndpoint("environment", null)))
                .isEqualTo(new JudgeEndpoint("environment", 1234));
    }

    @Test
    void Should_AllowTrioOnly_When_OneChildEnabled() {
        PiiDetectionConfig config = trioConfig(true, false, true, false);

        assertThat(config.trioEnabled()).isTrue();
        assertThat(config.cbxEnabled()).isTrue();
        assertThat(config.bardsEnabled()).isFalse();
        assertThat(config.ettinEnabled()).isFalse();
    }

    @Test
    void Should_RejectDisabledTrio_When_NoOtherDetectorEnabled() {
        assertThatThrownBy(() -> trioConfig(false, true, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one detector");
    }

    @Test
    void Should_RejectTrio_When_AllChildrenDisabled() {
        assertThatThrownBy(() -> trioConfig(true, false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trio");
    }

    private PiiDetectionConfig trioConfig(boolean trio, boolean bards, boolean cbx, boolean ettin) {
        return new PiiDetectionConfig(1, false, false, false, 2048, 410,
                new BigDecimal("0.30"), false, "localhost", 1234, null, null,
                1, false, null, LocalDateTime.now(), "test", trio, bards, cbx, ettin, null, null, null, null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void Should_ExposeDefaultJudgeModel_When_NotConfigured(String judgeModel) {
        PiiDetectionConfig config = new PiiDetectionConfig(1, true, true, true, 2048, 410,
                new BigDecimal("0.30"), true, "localhost", 1234, "ministral", judgeModel,
                1, true, null, LocalDateTime.now(), "system", false, true, true, true, null, null, null, null);

        assertThat(config.lmStudioJudgeModel()).isEqualTo("qwen3.8-flash-next-iq3_s");
        assertThat(config.lmStudioModel()).isEqualTo("ministral");
    }

    @Test
    void Should_PreserveJudgeModel_When_ExplicitlyConfigured() {
        PiiDetectionConfig config = new PiiDetectionConfig(1, true, true, true, 2048, 410,
                new BigDecimal("0.30"), true, "localhost", 1234, "ministral", "custom-judge",
                1, true, null, LocalDateTime.now(), "system", false, true, true, true, null, null, null, null);

        assertThat(config.lmStudioJudgeModel()).isEqualTo("custom-judge");
    }

    @Test
    void Should_CreateConfig_When_AllParametersValid() {
        // Arrange
        Integer id = 1;
        boolean presidioEnabled = true;
        boolean regexEnabled = false;
        BigDecimal threshold = new BigDecimal("0.75");
        LocalDateTime updatedAt = LocalDateTime.now();
        String updatedBy = "testuser";

        // Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            id, presidioEnabled, regexEnabled, false, 1024, 128, threshold, false, "localhost", 1234, null, null, 1, true, null, updatedAt, updatedBy, false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.id()).isEqualTo(id);
        softly.assertThat(config.presidioEnabled()).isEqualTo(presidioEnabled);
        softly.assertThat(config.regexEnabled()).isEqualTo(regexEnabled);
        softly.assertThat(config.ministralEnabled()).isFalse();
        softly.assertThat(config.ministralChunkSize()).isEqualTo(1024);
        softly.assertThat(config.ministralOverlap()).isEqualTo(128);
        softly.assertThat(config.defaultThreshold()).isEqualByComparingTo(threshold);
        softly.assertThat(config.postfilterEnabled()).isFalse();
        softly.assertThat(config.updatedAt()).isEqualTo(updatedAt);
        softly.assertThat(config.updatedBy()).isEqualTo(updatedBy);
        softly.assertAll();
    }

    @Test
    void Should_ExposePostfilterEnabledFlag_When_Enabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "system", false, true, true, true, null, null, null, null
        );

        // Assert
        assertThat(config.postfilterEnabled()).isTrue();
    }

    @Test
    void Should_ThrowException_When_ThresholdIsNull() {
        LocalDateTime now = LocalDateTime.now();
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, false, 1024, 128, null, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_ThrowException_When_ThresholdLessThanZero() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("-0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Default threshold must be greater than or equal to 0");
    }

    @Test
    void Should_ThrowException_When_ThresholdGreaterThanOne() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("1.1");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_AcceptThreshold_When_ThresholdIsZero() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, BigDecimal.ZERO, false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        assertThat(config.defaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void Should_AcceptThreshold_When_ThresholdIsOne() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, BigDecimal.ONE, false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        assertThat(config.defaultThreshold()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void Should_ThrowException_When_NoDetectorsEnabled() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, false, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one detector must be enabled");
    }

    @Test
    void Should_AcceptConfig_When_OnlyPresidioEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.presidioEnabled()).isTrue();
        softly.assertThat(config.regexEnabled()).isFalse();
        softly.assertThat(config.ministralEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_AcceptConfig_When_OnlyRegexEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.presidioEnabled()).isFalse();
        softly.assertThat(config.regexEnabled()).isTrue();
        softly.assertThat(config.ministralEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_AcceptConfig_When_OnlyMinistralEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, false, true, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.presidioEnabled()).isFalse();
        softly.assertThat(config.regexEnabled()).isFalse();
        softly.assertThat(config.ministralEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void Should_ThrowException_When_MinistralChunkSizeBelowMinimum() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 255, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral chunk size");
    }

    @Test
    void Should_ThrowException_When_MinistralChunkSizeAboveMaximum() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 4097, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral chunk size");
    }

    @Test
    void Should_ThrowException_When_MinistralChunkSizeIsNull() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, null, 128, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral chunk size");
    }

    @Test
    void Should_ThrowException_When_MinistralOverlapNegative() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 1024, -1, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral overlap");
    }

    @Test
    void Should_ThrowException_When_MinistralOverlapNotLessThanChunkSize() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 1024, 1024, defaultThreshold, false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral overlap");
    }

    @Test
    void Should_AcceptConfig_When_MinistralChunkAndOverlapWithinRange() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, false, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.ministralChunkSize()).isEqualTo(2048);
        softly.assertThat(config.ministralOverlap()).isEqualTo(256);
        softly.assertAll();
    }

    @Test
    void Should_ThrowException_When_MinistralConcurrencyIsNull() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, null, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral concurrency");
    }

    @Test
    void Should_ThrowException_When_MinistralConcurrencyBelowMinimum() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, 0, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral concurrency");
    }

    @Test
    void Should_ThrowException_When_MinistralConcurrencyAboveMaximum() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, null, 21, true, null, now, "testuser", false, true, true, true, null, null, null, null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral concurrency");
    }

    @Test
    void Should_AcceptConfig_When_MinistralConcurrencyAtBoundaries() {
        // Arrange & Act
        PiiDetectionConfig minConfig = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );
        PiiDetectionConfig maxConfig = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 20, false, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(minConfig.ministralConcurrency()).isEqualTo(1);
        softly.assertThat(minConfig.ministralConcurrencyAuto()).isTrue();
        softly.assertThat(maxConfig.ministralConcurrency()).isEqualTo(20);
        softly.assertThat(maxConfig.ministralConcurrencyAuto()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_AcceptConfig_When_MinistralConcurrencyTunedSignatureIsNullOrSet() {
        // Arrange & Act — null means "never tuned / re-tune at next startup".
        PiiDetectionConfig neverTuned = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );
        PiiDetectionConfig tuned = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 4, true, "localhost:1234|ministral", LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(neverTuned.ministralConcurrencyTunedSignature()).isNull();
        softly.assertThat(tuned.ministralConcurrencyTunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertAll();
    }

    @Test
    void Should_BeEqual_When_AllFieldsMatch() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig config1 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );

        // Act & Assert
        assertThat(config1)
            .isEqualTo(config2)
            .hasSameHashCodeAs(config2);
    }

    @Test
    void Should_NotBeEqual_When_PostfilterEnabledDiffers() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig configOff = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );
        PiiDetectionConfig configOn = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );

        // Act & Assert
        assertThat(configOff).isNotEqualTo(configOn);
    }

    @Test
    void Should_NotBeEqual_When_ThresholdDiffers() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig config1 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.80"), false, "localhost", 1234, null, null, 1, true, null, now, "testuser", false, true, true, true, null, null, null, null
        );

        // Act & Assert
        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    void Should_ReturnValidString_When_ToStringCalled() {
        // Arrange
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, null, 1, true, null, LocalDateTime.now(), "testuser", false, true, true, true, null, null, null, null
        );

        // Act
        String result = config.toString();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).contains("PiiDetectionConfig[");
        softly.assertThat(result).contains("id=1");
        softly.assertThat(result).contains("presidioEnabled=true");
        softly.assertThat(result).contains("regexEnabled=false");
        softly.assertThat(result).contains("ministralEnabled=false");
        softly.assertThat(result).contains("defaultThreshold=0.75");
        softly.assertThat(result).contains("postfilterEnabled=false");
        softly.assertThat(result).contains("updatedBy=testuser");
        softly.assertAll();
    }
}
