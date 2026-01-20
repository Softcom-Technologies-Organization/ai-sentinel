package pro.softcom.aisentinel.domain.pii.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PiiSeverity enum.
 */
class PersonallyIdentifiableInformationSeverityTest {

    @Test
    void Should_HaveThreeSeverityLevels() {
        // Act
        PersonallyIdentifiableInformationSeverity[] severities = PersonallyIdentifiableInformationSeverity.values();

        // Assert
        assertThat(severities)
            .hasSize(3)
            .containsExactlyInAnyOrder(
                PersonallyIdentifiableInformationSeverity.HIGH,
                PersonallyIdentifiableInformationSeverity.MEDIUM,
                PersonallyIdentifiableInformationSeverity.LOW
        );
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfHigh() {
        // Act
        PersonallyIdentifiableInformationSeverity severity = PersonallyIdentifiableInformationSeverity.valueOf("HIGH");

        // Assert
        assertThat(severity).isEqualTo(PersonallyIdentifiableInformationSeverity.HIGH);
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfMedium() {
        // Act
        PersonallyIdentifiableInformationSeverity severity = PersonallyIdentifiableInformationSeverity.valueOf("MEDIUM");

        // Assert
        assertThat(severity).isEqualTo(PersonallyIdentifiableInformationSeverity.MEDIUM);
    }

    @Test
    void Should_ReturnCorrectEnumValue_When_ValueOfLow() {
        // Act
        PersonallyIdentifiableInformationSeverity severity = PersonallyIdentifiableInformationSeverity.valueOf("LOW");

        // Assert
        assertThat(severity).isEqualTo(PersonallyIdentifiableInformationSeverity.LOW);
    }

    @Test
    void Should_HaveCorrectOrdinalValues() {
        // Assert - HIGH should be first (most severe)
        assertThat(PersonallyIdentifiableInformationSeverity.HIGH.ordinal()).isZero();
        assertThat(PersonallyIdentifiableInformationSeverity.MEDIUM.ordinal()).isEqualTo(1);
        assertThat(PersonallyIdentifiableInformationSeverity.LOW.ordinal()).isEqualTo(2);
    }

    @Test
    void Should_BeComparable() {
        // Assert - HIGH is more severe than MEDIUM
        assertThat(PersonallyIdentifiableInformationSeverity.HIGH.compareTo(
            PersonallyIdentifiableInformationSeverity.MEDIUM)).isLessThan(0);
        assertThat(PersonallyIdentifiableInformationSeverity.MEDIUM.compareTo(
            PersonallyIdentifiableInformationSeverity.LOW)).isLessThan(0);
        assertThat(PersonallyIdentifiableInformationSeverity.LOW.compareTo(
            PersonallyIdentifiableInformationSeverity.HIGH)).isGreaterThan(0);
    }
}
