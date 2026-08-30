package pro.softcom.aisentinel.domain.pii.detection;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PiiDetectionConfig domain model.
 */
class PiiDetectionConfigTest {

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
            id, presidioEnabled, regexEnabled, false, 1024, 128, threshold, false, "localhost", 1234, 1, true, null, updatedAt, updatedBy
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
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, 1, true, null, LocalDateTime.now(), "system"
        );

        // Assert
        assertThat(config.postfilterEnabled()).isTrue();
    }

    @Test
    void Should_ThrowException_When_ThresholdIsNull() {
        LocalDateTime now = LocalDateTime.now();
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, false, 1024, 128, null, false, "localhost", 1234, 1, true, null, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_ThrowException_When_ThresholdLessThanZero() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("-0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, false, 1024, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, true, false, 1024, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Should_AcceptThreshold_When_ThresholdIsZero() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, BigDecimal.ZERO, false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
        );

        // Assert
        assertThat(config.defaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void Should_AcceptThreshold_When_ThresholdIsOne() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, BigDecimal.ONE, false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
            1, false, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one detector must be enabled");
    }

    @Test
    void Should_AcceptConfig_When_OnlyPresidioEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
            1, false, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
            1, false, false, true, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
            1, true, false, false, 255, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, false, false, 4097, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, false, false, null, 128, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, false, false, 1024, -1, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, false, false, 1024, 1024, defaultThreshold, false, "localhost", 1234, 1, true, null, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral overlap");
    }

    @Test
    void Should_AcceptConfig_When_MinistralChunkAndOverlapWithinRange() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, false, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, null, true, null, now, "testuser"
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
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, 0, true, null, now, "testuser"
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
            1, true, false, false, 1024, 128, defaultThreshold, false, "localhost", 1234, 17, true, null, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ministral concurrency");
    }

    @Test
    void Should_AcceptConfig_When_MinistralConcurrencyAtBoundaries() {
        // Arrange & Act
        PiiDetectionConfig minConfig = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
        );
        PiiDetectionConfig maxConfig = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 16, false, null, LocalDateTime.now(), "testuser"
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(minConfig.ministralConcurrency()).isEqualTo(1);
        softly.assertThat(minConfig.ministralConcurrencyAuto()).isTrue();
        softly.assertThat(maxConfig.ministralConcurrency()).isEqualTo(16);
        softly.assertThat(maxConfig.ministralConcurrencyAuto()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_AcceptConfig_When_MinistralConcurrencyTunedSignatureIsNullOrSet() {
        // Arrange & Act — null means "never tuned / re-tune at next startup".
        PiiDetectionConfig neverTuned = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
        );
        PiiDetectionConfig tuned = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 4, true, "localhost:1234|ministral", LocalDateTime.now(), "testuser"
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
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, now, "testuser"
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, now, "testuser"
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
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, now, "testuser"
        );
        PiiDetectionConfig configOn = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, 1, true, null, now, "testuser"
        );

        // Act & Assert
        assertThat(configOff).isNotEqualTo(configOn);
    }

    @Test
    void Should_NotBeEqual_When_ThresholdDiffers() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig config1 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, now, "testuser"
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.80"), false, "localhost", 1234, 1, true, null, now, "testuser"
        );

        // Act & Assert
        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    void Should_ReturnValidString_When_ToStringCalled() {
        // Arrange
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, 1, true, null, LocalDateTime.now(), "testuser"
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
