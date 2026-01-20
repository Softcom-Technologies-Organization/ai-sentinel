package pro.softcom.aisentinel.domain.pii.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityCountsTest {

    @Test
    void Should_CreateSeverityCounts_When_AllValuesProvided() {
        // Arrange & Act
        SeverityCounts counts = new SeverityCounts(5, 10, 3);

        // Assert
        assertThat(counts.high()).isEqualTo(5);
        assertThat(counts.medium()).isEqualTo(10);
        assertThat(counts.low()).isEqualTo(3);
    }

    @Test
    void Should_CalculateTotalCorrectly() {
        // Arrange
        SeverityCounts counts = new SeverityCounts(5, 10, 3);

        // Act
        int total = counts.total();

        // Assert
        assertThat(total).isEqualTo(18);
    }

    @Test
    void Should_ReturnZero_When_AllCountsAreZero() {
        // Arrange
        SeverityCounts counts = new SeverityCounts(0, 0, 0);

        // Act
        int total = counts.total();

        // Assert
        assertThat(total).isZero();
    }

    @Test
    void Should_CreateZeroSeverityCounts_When_CallingZeroFactory() {
        // Act
        SeverityCounts counts = SeverityCounts.zero();

        // Assert
        assertThat(counts.high()).isZero();
        assertThat(counts.medium()).isZero();
        assertThat(counts.low()).isZero();
        assertThat(counts.total()).isZero();
    }


    @Test
    void Should_ImplementEqualsCorrectly() {
        // Arrange
        SeverityCounts counts1 = new SeverityCounts(5, 10, 3);
        SeverityCounts counts2 = new SeverityCounts(5, 10, 3);
        SeverityCounts counts3 = new SeverityCounts(5, 10, 4);

        // Assert
        assertThat(counts1)
            .isEqualTo(counts2)
            .isNotEqualTo(counts3);
    }

    @Test
    void Should_ImplementHashCodeCorrectly() {
        // Arrange
        SeverityCounts counts1 = new SeverityCounts(5, 10, 3);
        SeverityCounts counts2 = new SeverityCounts(5, 10, 3);

        // Assert
        assertThat(counts1).hasSameHashCodeAs(counts2);
    }

    @Test
    void Should_ImplementToStringCorrectly() {
        // Arrange
        SeverityCounts counts = new SeverityCounts(5, 10, 3);

        // Act
        String toString = counts.toString();

        // Assert
        assertThat(toString)
            .contains("5")
            .contains("10")
            .contains("3");
    }
}
