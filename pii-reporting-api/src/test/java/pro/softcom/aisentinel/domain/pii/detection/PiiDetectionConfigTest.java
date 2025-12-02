package pro.softcom.aisentinel.domain.pii.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PiiDetectionConfig domain model.
 */
class PiiDetectionConfigTest {

    @Test
    void should_CreateConfig_When_AllParametersValid() {
        // Arrange
        Integer id = 1;
        boolean glinerEnabled = true;
        boolean presidioEnabled = true;
        boolean regexEnabled = false;
        BigDecimal threshold = new BigDecimal("0.75");
        LocalDateTime updatedAt = LocalDateTime.now();
        String updatedBy = "testuser";

        // Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            id, glinerEnabled, presidioEnabled, regexEnabled, threshold, updatedAt, updatedBy
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.getId()).isEqualTo(id);
        softly.assertThat(config.isGlinerEnabled()).isEqualTo(glinerEnabled);
        softly.assertThat(config.isPresidioEnabled()).isEqualTo(presidioEnabled);
        softly.assertThat(config.isRegexEnabled()).isEqualTo(regexEnabled);
        softly.assertThat(config.getDefaultThreshold()).isEqualByComparingTo(threshold);
        softly.assertThat(config.getUpdatedAt()).isEqualTo(updatedAt);
        softly.assertThat(config.getUpdatedBy()).isEqualTo(updatedBy);
        softly.assertAll();
    }

    @Test
    void should_ThrowException_When_ThresholdIsNull() {
        LocalDateTime now = LocalDateTime.now();
        // Arrange & Act & Assert
        assertThatThrownBy(() -> {
            new PiiDetectionConfig(
                1, true, true, true, null, now, "testuser"
            );
        })
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_ThrowException_When_ThresholdLessThanZero() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("-0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, true, defaultThreshold, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Default threshold must be greater than or equal to 0");
    }

    @Test
    void should_ThrowException_When_ThresholdGreaterThanOne() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("1.1");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new PiiDetectionConfig(
            1, true, true, true, defaultThreshold, now, "testuser"
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_AcceptThreshold_When_ThresholdIsZero() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, BigDecimal.ZERO, LocalDateTime.now(), "testuser"
        );

        // Assert
        assertThat(config.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_AcceptThreshold_When_ThresholdIsOne() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, BigDecimal.ONE, LocalDateTime.now(), "testuser"
        );

        // Assert
        assertThat(config.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void should_ThrowException_When_NoDetectorsEnabled() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal defaultThreshold = new BigDecimal("0.75");
        // Arrange & Act & Assert
        assertThatThrownBy(() -> {
            new PiiDetectionConfig(
                1, false, false, false, defaultThreshold, now, "testuser"
            );
        })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one detector must be enabled");
    }

    @Test
    void should_AcceptConfig_When_OnlyGlinerEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, false, false, new BigDecimal("0.75"), LocalDateTime.now(), "testuser"
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.isGlinerEnabled()).isTrue();
        softly.assertThat(config.isPresidioEnabled()).isFalse();
        softly.assertThat(config.isRegexEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void should_AcceptConfig_When_OnlyPresidioEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, true, false, new BigDecimal("0.75"), LocalDateTime.now(), "testuser"
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.isGlinerEnabled()).isFalse();
        softly.assertThat(config.isPresidioEnabled()).isTrue();
        softly.assertThat(config.isRegexEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void should_AcceptConfig_When_OnlyRegexEnabled() {
        // Arrange & Act
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, false, false, true, new BigDecimal("0.75"), LocalDateTime.now(), "testuser"
        );

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config.isGlinerEnabled()).isFalse();
        softly.assertThat(config.isPresidioEnabled()).isFalse();
        softly.assertThat(config.isRegexEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void should_BeEqual_When_AllFieldsMatch() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig config1 = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.75"), now, "testuser"
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.75"), now, "testuser"
        );

        // Act & Assert
        assertThat(config1)
            .isEqualTo(config2)
            .hasSameHashCodeAs(config2);
    }

    @Test
    void should_NotBeEqual_When_ThresholdDiffers() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PiiDetectionConfig config1 = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.75"), now, "testuser"
        );
        PiiDetectionConfig config2 = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.80"), now, "testuser"
        );

        // Act & Assert
        assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    void should_ReturnValidString_When_ToStringCalled() {
        // Arrange
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.75"), LocalDateTime.now(), "testuser"
        );

        // Act
        String result = config.toString();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).contains("PiiDetectionConfig{");
        softly.assertThat(result).contains("id=1");
        softly.assertThat(result).contains("glinerEnabled=true");
        softly.assertThat(result).contains("presidioEnabled=true");
        softly.assertThat(result).contains("regexEnabled=false");
        softly.assertThat(result).contains("defaultThreshold=0.75");
        softly.assertThat(result).contains("updatedBy='testuser'");
        softly.assertAll();
    }
}
