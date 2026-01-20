package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SeverityCountsDto;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityCountsMapperTest {

    private SeverityCountsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SeverityCountsMapper();
    }

    @Test
    void should_ReturnZeroDto_When_DomainObjectIsNull() {
        // When
        SeverityCountsDto result = mapper.toDto(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.high()).isZero();
        assertThat(result.medium()).isZero();
        assertThat(result.low()).isZero();
        assertThat(result.total()).isZero();
    }

    @Test
    void should_MapAllFields_When_DomainObjectHasValues() {
        // Given
        SeverityCounts domain = new SeverityCounts(5, 12, 8);

        // When
        SeverityCountsDto result = mapper.toDto(domain);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.high()).isEqualTo(5);
        assertThat(result.medium()).isEqualTo(12);
        assertThat(result.low()).isEqualTo(8);
    }

    @Test
    void should_CalculateTotalCorrectly_When_MappingDomainObject() {
        // Given
        SeverityCounts domain = new SeverityCounts(10, 20, 15);
        int expectedTotal = 45;

        // When
        SeverityCountsDto result = mapper.toDto(domain);

        // Then
        assertThat(result.total()).isEqualTo(expectedTotal);
        assertThat(result.total()).isEqualTo(result.high() + result.medium() + result.low());
    }

    @Test
    void should_MapZeroCounts_When_DomainObjectIsZero() {
        // Given
        SeverityCounts domain = SeverityCounts.zero();

        // When
        SeverityCountsDto result = mapper.toDto(domain);

        // Then
        assertThat(result.high()).isZero();
        assertThat(result.medium()).isZero();
        assertThat(result.low()).isZero();
        assertThat(result.total()).isZero();
    }

    @Test
    void should_HandleLargeValues_When_DomainObjectHasHighCounts() {
        // Given
        SeverityCounts domain = new SeverityCounts(1000, 2500, 750);

        // When
        SeverityCountsDto result = mapper.toDto(domain);

        // Then
        assertThat(result.high()).isEqualTo(1000);
        assertThat(result.medium()).isEqualTo(2500);
        assertThat(result.low()).isEqualTo(750);
        assertThat(result.total()).isEqualTo(4250);
    }
}
