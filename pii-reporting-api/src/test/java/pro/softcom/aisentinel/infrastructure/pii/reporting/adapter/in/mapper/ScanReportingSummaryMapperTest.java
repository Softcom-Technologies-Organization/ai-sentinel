package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.domain.pii.reporting.SpaceSummary;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanReportingSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SeverityCountsDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SpaceSummaryDto;

/**
 * Tests unitaires pour ScanReportingSummaryMapper.
 * 
 * <p>Vérifie la transformation correcte des objets de domaine en DTOs REST,
 * incluant l'enrichissement avec les données de severity counts.
 */
@ExtendWith(MockitoExtension.class)
class ScanReportingSummaryMapperTest {

    @Mock
    private ScanSeverityCountService severityCountService;

    @Mock
    private SeverityCountsMapper severityCountsMapper;

    private ScanReportingSummaryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ScanReportingSummaryMapper(severityCountService, severityCountsMapper);
    }

    @Test
    void should_MapToDto_When_SummaryIsValid() {
        // Given
        String scanId = "scan-123";
        Instant lastUpdated = Instant.parse("2025-01-15T10:00:00Z");
        
        SpaceSummary space = new SpaceSummary(
                "SPACE1",
                "IN_PROGRESS",
                75.5,
                100L,
                50L,
                Instant.parse("2025-01-15T09:30:00Z")
        );
        
        ScanReportingSummary summary = new ScanReportingSummary(
                scanId,
                lastUpdated,
                1,
                List.of(space)
        );
        
        SeverityCounts severityCounts = new SeverityCounts(10, 20, 5);
        SeverityCountsDto severityCountsDto = new SeverityCountsDto(10, 20, 5, 35);
        
        when(severityCountService.getCounts(scanId, "SPACE1"))
                .thenReturn(Optional.of(severityCounts));
        when(severityCountsMapper.toDto(severityCounts))
                .thenReturn(severityCountsDto);
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.scanId()).isEqualTo(scanId);
        assertThat(result.lastUpdated()).isEqualTo(lastUpdated);
        assertThat(result.spacesCount()).isEqualTo(1);
        assertThat(result.spaces()).hasSize(1);
        
        SpaceSummaryDto spaceDto = result.spaces().get(0);
        assertThat(spaceDto.spaceKey()).isEqualTo("SPACE1");
        assertThat(spaceDto.status()).isEqualTo("IN_PROGRESS");
        assertThat(spaceDto.progressPercentage()).isEqualTo(75.5);
        assertThat(spaceDto.pagesDone()).isEqualTo(100L);
        assertThat(spaceDto.attachmentsDone()).isEqualTo(50L);
        assertThat(spaceDto.lastEventTs()).isEqualTo(Instant.parse("2025-01-15T09:30:00Z"));
        assertThat(spaceDto.severityCounts()).isEqualTo(severityCountsDto);
        
        verify(severityCountService).getCounts(scanId, "SPACE1");
        verify(severityCountsMapper).toDto(severityCounts);
    }

    @Test
    void should_MapWithZeroSeverityCounts_When_CountsNotFound() {
        // Given
        String scanId = "scan-123";
        
        SpaceSummary space = new SpaceSummary(
                "SPACE1",
                "COMPLETED",
                100.0,
                200L,
                100L,
                Instant.parse("2025-01-15T10:00:00Z")
        );
        
        ScanReportingSummary summary = new ScanReportingSummary(
                scanId,
                Instant.parse("2025-01-15T10:00:00Z"),
                1,
                List.of(space)
        );
        
        SeverityCountsDto zeroCountsDto = SeverityCountsDto.zero();
        
        when(severityCountService.getCounts(scanId, "SPACE1"))
                .thenReturn(Optional.empty());
        when(severityCountsMapper.toDto(null))
                .thenReturn(zeroCountsDto);
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaces()).hasSize(1);
        
        SpaceSummaryDto spaceDto = result.spaces().get(0);
        assertThat(spaceDto.severityCounts()).isEqualTo(zeroCountsDto);
        
        verify(severityCountService).getCounts(scanId, "SPACE1");
        verify(severityCountsMapper).toDto(null);
    }

    @Test
    void should_MapMultipleSpaces_When_SummaryHasMultipleSpaces() {
        // Given
        String scanId = "scan-456";
        
        SpaceSummary space1 = new SpaceSummary(
                "SPACE1",
                "COMPLETED",
                100.0,
                200L,
                100L,
                Instant.parse("2025-01-15T10:00:00Z")
        );
        
        SpaceSummary space2 = new SpaceSummary(
                "SPACE2",
                "IN_PROGRESS",
                50.0,
                100L,
                50L,
                Instant.parse("2025-01-15T09:00:00Z")
        );
        
        ScanReportingSummary summary = new ScanReportingSummary(
                scanId,
                Instant.parse("2025-01-15T10:00:00Z"),
                2,
                List.of(space1, space2)
        );
        
        SeverityCounts counts1 = new SeverityCounts(5, 10, 2);
        SeverityCounts counts2 = new SeverityCounts(3, 7, 1);
        SeverityCountsDto countsDto1 = new SeverityCountsDto(5, 10, 2, 17);
        SeverityCountsDto countsDto2 = new SeverityCountsDto(3, 7, 1, 11);
        
        when(severityCountService.getCounts(scanId, "SPACE1"))
                .thenReturn(Optional.of(counts1));
        when(severityCountService.getCounts(scanId, "SPACE2"))
                .thenReturn(Optional.of(counts2));
        when(severityCountsMapper.toDto(counts1)).thenReturn(countsDto1);
        when(severityCountsMapper.toDto(counts2)).thenReturn(countsDto2);
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result.spaces()).hasSize(2);
        assertThat(result.spaces().get(0).severityCounts()).isEqualTo(countsDto1);
        assertThat(result.spaces().get(1).severityCounts()).isEqualTo(countsDto2);
        
        verify(severityCountService).getCounts(scanId, "SPACE1");
        verify(severityCountService).getCounts(scanId, "SPACE2");
        verify(severityCountsMapper).toDto(counts1);
        verify(severityCountsMapper).toDto(counts2);
    }

    @Test
    void should_ReturnNull_When_SummaryIsNull() {
        // Given
        ScanReportingSummary summary = null;
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNull();
        verify(severityCountService, never()).getCounts(any(), any());
        verify(severityCountsMapper, never()).toDto(any());
    }

    @Test
    void should_ReturnEmptySpacesList_When_SpacesIsNull() {
        // Given
        ScanReportingSummary summary = new ScanReportingSummary(
                "scan-123",
                Instant.parse("2025-01-15T10:00:00Z"),
                0,
                null
        );
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaces()).isEmpty();
        verify(severityCountService, never()).getCounts(any(), any());
        verify(severityCountsMapper, never()).toDto(any());
    }

    @Test
    void should_ReturnEmptySpacesList_When_SpacesIsEmpty() {
        // Given
        ScanReportingSummary summary = new ScanReportingSummary(
                "scan-123",
                Instant.parse("2025-01-15T10:00:00Z"),
                0,
                List.of()
        );
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaces()).isEmpty();
        verify(severityCountService, never()).getCounts(any(), any());
        verify(severityCountsMapper, never()).toDto(any());
    }

    @Test
    void should_HandleNullSpace_When_SpaceIsNullInList() {
        // Given
        ScanReportingSummary summary = new ScanReportingSummary(
                "scan-123",
                Instant.parse("2025-01-15T10:00:00Z"),
                1,
                Arrays.asList((SpaceSummary) null)
        );
        
        // When
        ScanReportingSummaryDto result = mapper.toDto(summary);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaces()).hasSize(1);
        assertThat(result.spaces().get(0)).isNull();
        verify(severityCountService, never()).getCounts(any(), any());
        verify(severityCountsMapper, never()).toDto(any());
    }
}
