package pro.softcom.aisentinel.domain.pii.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PiiSeverity enum.
 */
class PiiSeverityTest {

    @Test
    void Should_HaveThreeSeverityLevels() {
        // Act
        PiiSeverity[] severities = PiiSeverity.values();

        // Assert
        assertThat(severities)
            .hasSize(3)
            .containsExactlyInAnyOrder(
            PiiSeverity.HIGH,
            PiiSeverity.MEDIUM,
            PiiSeverity.LOW
        );
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfHigh() {
        // Act
        PiiSeverity severity = PiiSeverity.valueOf("HIGH");

        // Assert
        assertThat(severity).isEqualTo(PiiSeverity.HIGH);
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfMedium() {
        // Act
        PiiSeverity severity = PiiSeverity.valueOf("MEDIUM");

        // Assert
        assertThat(severity).isEqualTo(PiiSeverity.MEDIUM);
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfLow() {
        // Act
        PiiSeverity severity = PiiSeverity.valueOf("LOW");

        // Assert
        assertThat(severity).isEqualTo(PiiSeverity.LOW);
    }

    @Test
    void Should_HaveCorrectOrdinalValues() {
        // Assert - HIGH should be first (most severe)
        assertThat(PiiSeverity.HIGH.ordinal()).isZero();
        assertThat(PiiSeverity.MEDIUM.ordinal()).isEqualTo(1);
        assertThat(PiiSeverity.LOW.ordinal()).isEqualTo(2);
    }

    @Test
    void Should_BeComparable() {
        // Assert - HIGH is more severe than MEDIUM
        assertThat(PiiSeverity.HIGH.compareTo(PiiSeverity.MEDIUM)).isLessThan(0);
        assertThat(PiiSeverity.MEDIUM.compareTo(PiiSeverity.LOW)).isLessThan(0);
        assertThat(PiiSeverity.LOW.compareTo(PiiSeverity.HIGH)).isGreaterThan(0);
    }
}
