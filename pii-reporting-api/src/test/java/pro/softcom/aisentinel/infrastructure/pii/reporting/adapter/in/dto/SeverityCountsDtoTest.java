package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeverityCountsDtoTest {

    @Test
    void should_CreateValidDto_When_AllCountsArePositive() {
        // Given
        int high = 5;
        int medium = 12;
        int low = 8;
        int total = 25;

        // When
        SeverityCountsDto dto = new SeverityCountsDto(high, medium, low, total);

        // Then
        assertThat(dto.high()).isEqualTo(5);
        assertThat(dto.medium()).isEqualTo(12);
        assertThat(dto.low()).isEqualTo(8);
        assertThat(dto.total()).isEqualTo(25);
    }

    @Test
    void should_CreateValidDto_When_AllCountsAreZero() {
        // When
        SeverityCountsDto dto = new SeverityCountsDto(0, 0, 0, 0);

        // Then
        assertThat(dto.high()).isZero();
        assertThat(dto.medium()).isZero();
        assertThat(dto.low()).isZero();
        assertThat(dto.total()).isZero();
    }

    @Test
    void should_ThrowException_When_HighCountIsNegative() {
        // When / Then
        assertThatThrownBy(() -> new SeverityCountsDto(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Severity counts cannot be negative");
    }

    @Test
    void should_ThrowException_When_MediumCountIsNegative() {
        // When / Then
        assertThatThrownBy(() -> new SeverityCountsDto(0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Severity counts cannot be negative");
    }

    @Test
    void should_ThrowException_When_LowCountIsNegative() {
        // When / Then
        assertThatThrownBy(() -> new SeverityCountsDto(0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Severity counts cannot be negative");
    }

    @Test
    void should_ThrowException_When_MultipleCountsAreNegative() {
        // When / Then
        assertThatThrownBy(() -> new SeverityCountsDto(-5, -10, -3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Severity counts cannot be negative");
    }

    @Test
    void should_ReturnZeroInstance_When_UsingZeroFactoryMethod() {
        // When
        SeverityCountsDto zero = SeverityCountsDto.zero();

        // Then
        assertThat(zero.high()).isZero();
        assertThat(zero.medium()).isZero();
        assertThat(zero.low()).isZero();
        assertThat(zero.total()).isZero();
    }

    @Test
    void should_HaveCorrectTotalValue_When_CreatedWithCountValues() {
        // Given
        int high = 10;
        int medium = 20;
        int low = 15;
        int expectedTotal = 45;

        // When
        SeverityCountsDto dto = new SeverityCountsDto(high, medium, low, expectedTotal);

        // Then
        assertThat(dto.total()).isEqualTo(expectedTotal);
        assertThat(dto.high() + dto.medium() + dto.low()).isEqualTo(expectedTotal);
    }
}
