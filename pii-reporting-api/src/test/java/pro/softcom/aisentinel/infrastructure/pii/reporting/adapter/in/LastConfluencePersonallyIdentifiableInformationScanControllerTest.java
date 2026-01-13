package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanReportingSummary;
import pro.softcom.aisentinel.domain.pii.reporting.SpaceSummary;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanReportingSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SeverityCountsDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.SpaceSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ConfluenceContentScanResultToScanEventMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.LastScanMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanReportingSummaryMapper;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.SpaceStatusMapper;
import org.springframework.context.annotation.Import;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;

@WebMvcTest(LastConfluencePersonallyIdentifiableInformationScanController.class)
@Import(SecurityConfig.class)
class LastConfluencePersonallyIdentifiableInformationScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanReportingPort scanReportingPort;

    @MockitoBean
    private LastScanMapper lastScanMapper;

    @MockitoBean
    private SpaceStatusMapper spaceStatusMapper;

    @MockitoBean
    private ConfluenceContentScanResultToScanEventMapper confluenceContentScanResultToScanEventMapper;

    @MockitoBean
    private ScanReportingSummaryMapper scanReportingSummaryMapper;

    @Test
    void should_ReturnSpacesSummaryWithSeverityCounts_When_ScanExists() throws Exception {
        // Arrange
        String scanId = "scan-123";
        Instant lastUpdated = Instant.parse("2025-01-15T10:00:00Z");
        Instant lastEventTs = Instant.parse("2025-01-15T09:30:00Z");

        LastScanMeta metadata = new LastScanMeta(scanId, lastUpdated, 2);
        ScanReportingSummary domainSummary = new ScanReportingSummary(
            scanId,
            lastUpdated,
            2,
            List.of(
                new SpaceSummary("SPACE1", "COMPLETED", 100.0, 10L, 5L, lastEventTs),
                new SpaceSummary("SPACE2", "IN_PROGRESS", 50.0, 5L, 2L, lastEventTs)
            )
        );

        ScanReportingSummaryDto dto = new ScanReportingSummaryDto(
            scanId,
            lastUpdated,
            2,
            List.of(
                new SpaceSummaryDto(
                    "SPACE1",
                    "COMPLETED",
                    100.0,
                    10L,
                    5L,
                    lastEventTs,
                    new SeverityCountsDto(5, 10, 15, 30)
                ),
                new SpaceSummaryDto(
                    "SPACE2",
                    "IN_PROGRESS",
                    50.0,
                    5L,
                    2L,
                    lastEventTs,
                    new SeverityCountsDto(2, 8, 12, 22)
                )
            )
        );

        when(scanReportingPort.getLatestScan()).thenReturn(Optional.of(metadata));
        when(scanReportingPort.getScanReportingSummary(scanId)).thenReturn(Optional.of(domainSummary));
        when(scanReportingSummaryMapper.toDto(domainSummary)).thenReturn(dto);

        // Act & Assert
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.scanId").value(scanId))
            .andExpect(jsonPath("$.spacesCount").value(2))
            .andExpect(jsonPath("$.spaces").isArray())
            .andExpect(jsonPath("$.spaces.length()").value(2))
            // Verify SPACE1 severity counts
            .andExpect(jsonPath("$.spaces[0].spaceKey").value("SPACE1"))
            .andExpect(jsonPath("$.spaces[0].severityCounts.high").value(5))
            .andExpect(jsonPath("$.spaces[0].severityCounts.medium").value(10))
            .andExpect(jsonPath("$.spaces[0].severityCounts.low").value(15))
            .andExpect(jsonPath("$.spaces[0].severityCounts.total").value(30))
            // Verify SPACE2 severity counts
            .andExpect(jsonPath("$.spaces[1].spaceKey").value("SPACE2"))
            .andExpect(jsonPath("$.spaces[1].severityCounts.high").value(2))
            .andExpect(jsonPath("$.spaces[1].severityCounts.medium").value(8))
            .andExpect(jsonPath("$.spaces[1].severityCounts.low").value(12))
            .andExpect(jsonPath("$.spaces[1].severityCounts.total").value(22));

        verify(scanReportingPort).getLatestScan();
        verify(scanReportingPort).getScanReportingSummary(scanId);
        verify(scanReportingSummaryMapper).toDto(domainSummary);
    }

    @Test
    void should_ReturnSpacesSummaryWithZeroSeverityCounts_When_NoSeverityCountsExist() throws Exception {
        // Arrange
        String scanId = "scan-456";
        Instant lastUpdated = Instant.parse("2025-01-15T10:00:00Z");
        Instant lastEventTs = Instant.parse("2025-01-15T09:30:00Z");

        LastScanMeta metadata = new LastScanMeta(scanId, lastUpdated, 1);
        ScanReportingSummary domainSummary = new ScanReportingSummary(
            scanId,
            lastUpdated,
            1,
            List.of(new SpaceSummary("SPACE3", "COMPLETED", 100.0, 5L, 0L, lastEventTs))
        );

        ScanReportingSummaryDto dto = new ScanReportingSummaryDto(
            scanId,
            lastUpdated,
            1,
            List.of(
                new SpaceSummaryDto(
                    "SPACE3",
                    "COMPLETED",
                    100.0,
                    5L,
                    0L,
                    lastEventTs,
                    SeverityCountsDto.zero()  // No severity counts found
                )
            )
        );

        when(scanReportingPort.getLatestScan()).thenReturn(Optional.of(metadata));
        when(scanReportingPort.getScanReportingSummary(scanId)).thenReturn(Optional.of(domainSummary));
        when(scanReportingSummaryMapper.toDto(domainSummary)).thenReturn(dto);

        // Act & Assert
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.spaces[0].severityCounts.high").value(0))
            .andExpect(jsonPath("$.spaces[0].severityCounts.medium").value(0))
            .andExpect(jsonPath("$.spaces[0].severityCounts.low").value(0))
            .andExpect(jsonPath("$.spaces[0].severityCounts.total").value(0));

        verify(scanReportingSummaryMapper).toDto(domainSummary);
    }

    @Test
    void should_ReturnEmptySpacesList_When_SummaryHasNoSpaces() throws Exception {
        // Arrange
        String scanId = "scan-789";
        Instant lastUpdated = Instant.parse("2025-01-15T10:00:00Z");

        LastScanMeta metadata = new LastScanMeta(scanId, lastUpdated, 0);
        ScanReportingSummary domainSummary = new ScanReportingSummary(
            scanId,
            lastUpdated,
            0,
            List.of()  // No spaces
        );

        ScanReportingSummaryDto dto = new ScanReportingSummaryDto(
            scanId,
            lastUpdated,
            0,
            List.of()
        );

        when(scanReportingPort.getLatestScan()).thenReturn(Optional.of(metadata));
        when(scanReportingPort.getScanReportingSummary(scanId)).thenReturn(Optional.of(domainSummary));
        when(scanReportingSummaryMapper.toDto(domainSummary)).thenReturn(dto);

        // Act & Assert
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.scanId").value(scanId))
            .andExpect(jsonPath("$.spacesCount").value(0))
            .andExpect(jsonPath("$.spaces").isArray())
            .andExpect(jsonPath("$.spaces").isEmpty());

        verify(scanReportingSummaryMapper).toDto(domainSummary);
    }

    @Test
    void should_ReturnNoContent_When_NoScanExists() throws Exception {
        // Arrange
        when(scanReportingPort.getLatestScan()).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        verify(scanReportingPort).getLatestScan();
    }

    @Test
    void should_ReturnNoContent_When_ScanExistsButSummaryNotFound() throws Exception {
        // Arrange
        String scanId = "scan-999";
        Instant lastUpdated = Instant.parse("2025-01-15T10:00:00Z");

        LastScanMeta metadata = new LastScanMeta(scanId, lastUpdated, 0);

        when(scanReportingPort.getLatestScan()).thenReturn(Optional.of(metadata));
        when(scanReportingPort.getScanReportingSummary(scanId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        verify(scanReportingPort).getLatestScan();
        verify(scanReportingPort).getScanReportingSummary(scanId);
    }

    @Test
    void should_VerifyCompleteJsonStructure_When_ResponseIsSuccessful() throws Exception {
        // Arrange
        String scanId = "scan-full";
        Instant lastUpdated = Instant.parse("2025-01-15T12:00:00Z");
        Instant lastEventTs = Instant.parse("2025-01-15T11:45:00Z");

        LastScanMeta metadata = new LastScanMeta(scanId, lastUpdated, 1);
        ScanReportingSummary domainSummary = new ScanReportingSummary(
            scanId,
            lastUpdated,
            1,
            List.of(new SpaceSummary("COMPLETE", "COMPLETED", 100.0, 20L, 10L, lastEventTs))
        );

        ScanReportingSummaryDto dto = new ScanReportingSummaryDto(
            scanId,
            lastUpdated,
            1,
            List.of(
                new SpaceSummaryDto(
                    "COMPLETE",
                    "COMPLETED",
                    100.0,
                    20L,
                    10L,
                    lastEventTs,
                    new SeverityCountsDto(3, 7, 11, 21)
                )
            )
        );

        when(scanReportingPort.getLatestScan()).thenReturn(Optional.of(metadata));
        when(scanReportingPort.getScanReportingSummary(scanId)).thenReturn(Optional.of(domainSummary));
        when(scanReportingSummaryMapper.toDto(domainSummary)).thenReturn(dto);

        // Act & Assert - Verify complete JSON structure
        mockMvc.perform(get("/api/v1/scans/dashboard/spaces-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            // Root level
            .andExpect(jsonPath("$.scanId").value(scanId))
            .andExpect(jsonPath("$.lastUpdated").value("2025-01-15T12:00:00Z"))
            .andExpect(jsonPath("$.spacesCount").value(1))
            .andExpect(jsonPath("$.spaces").isArray())
            // Space level - all 7 fields
            .andExpect(jsonPath("$.spaces[0].spaceKey").value("COMPLETE"))
            .andExpect(jsonPath("$.spaces[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.spaces[0].progressPercentage").value(100.0))
            .andExpect(jsonPath("$.spaces[0].pagesDone").value(20))
            .andExpect(jsonPath("$.spaces[0].attachmentsDone").value(10))
            .andExpect(jsonPath("$.spaces[0].lastEventTs").value("2025-01-15T11:45:00Z"))
            // Severity counts level - all 4 fields
            .andExpect(jsonPath("$.spaces[0].severityCounts").exists())
            .andExpect(jsonPath("$.spaces[0].severityCounts.high").value(3))
            .andExpect(jsonPath("$.spaces[0].severityCounts.medium").value(7))
            .andExpect(jsonPath("$.spaces[0].severityCounts.low").value(11))
            .andExpect(jsonPath("$.spaces[0].severityCounts.total").value(21));
    }
}