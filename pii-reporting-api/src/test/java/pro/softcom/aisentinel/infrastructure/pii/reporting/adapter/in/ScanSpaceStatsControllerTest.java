package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.pii.reporting.port.in.GetScanSpaceStatsPort;
import pro.softcom.aisentinel.domain.pii.reporting.FailedScanItem;
import pro.softcom.aisentinel.domain.pii.reporting.FailedScanItem.ItemType;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper.ScanSpaceStatsMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScanSpaceStatsController.class)
@Import({SecurityConfig.class, ScanSpaceStatsMapper.class})
class ScanSpaceStatsControllerTest {

    private static final String STATS_URL = "/api/v1/scans/dashboard/spaces/KEY/stats";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetScanSpaceStatsPort getScanSpaceStatsPort;

    @Test
    void Should_Return200WithStatsAndComputedFields_When_StatsExist() throws Exception {
        ScanSpaceStats stats = new ScanSpaceStats(
            "uuid", "KEY",
            Instant.parse("2026-06-07T10:00:00Z"), Instant.parse("2026-06-07T10:12:34Z"),
            42, 1, 1_200_000L, 7, 2, 530_000L,
            List.of(new ScanDetectorStat("MINISTRAL", 12, 1_730_000L, 520_000L, 0, 0, null)),
            List.of(new FailedScanItem(ItemType.ATTACHMENT, "file.pdf")));
        when(getScanSpaceStatsPort.getLatestSpaceStats("KEY")).thenReturn(Optional.of(stats));

        mockMvc.perform(get(STATS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanId").value("uuid"))
            .andExpect(jsonPath("$.spaceKey").value("KEY"))
            .andExpect(jsonPath("$.durationMs").value(754000))
            .andExpect(jsonPath("$.pagesScanned").value(42))
            .andExpect(jsonPath("$.attachmentsFailed").value(2))
            .andExpect(jsonPath("$.detectorStats[0].detector").value("MINISTRAL"))
            .andExpect(jsonPath("$.detectorStats[0].charsPerSecond").value(3326.9))
            .andExpect(jsonPath("$.detectorStats[0].discarded").value(0))
            .andExpect(jsonPath("$.failedItems[0].itemType").value("ATTACHMENT"))
            .andExpect(jsonPath("$.failedItems[0].title").value("file.pdf"));
    }

    @Test
    void Should_ReturnNullDuration_When_ScanStillRunning() throws Exception {
        ScanSpaceStats stats = new ScanSpaceStats(
            "uuid", "KEY", Instant.parse("2026-06-07T10:00:00Z"), null,
            5, 0, 1000L, 0, 0, 0L, List.of(), List.of());
        when(getScanSpaceStatsPort.getLatestSpaceStats("KEY")).thenReturn(Optional.of(stats));

        mockMvc.perform(get(STATS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.durationMs").doesNotExist())
            .andExpect(jsonPath("$.finishedAt").doesNotExist());
    }

    @Test
    void Should_Return404_When_NoStatsForSpace() throws Exception {
        when(getScanSpaceStatsPort.getLatestSpaceStats("KEY")).thenReturn(Optional.empty());

        mockMvc.perform(get(STATS_URL))
            .andExpect(status().isNotFound());
    }
}
