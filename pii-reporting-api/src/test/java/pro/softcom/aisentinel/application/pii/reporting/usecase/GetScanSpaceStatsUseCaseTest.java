package pro.softcom.aisentinel.application.pii.reporting.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.out.FailedScanItemQuery;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSpaceStatsRepository;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.reporting.FailedScanItem;
import pro.softcom.aisentinel.domain.pii.reporting.FailedScanItem.ItemType;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetScanSpaceStatsUseCaseTest {

    private static final String SPACE_KEY = "KEY";
    private static final String SCAN_ID = "scan-1";

    @Mock
    private ScanCheckpointRepository checkpointRepository;

    @Mock
    private ScanSpaceStatsRepository statsRepository;

    @Mock
    private FailedScanItemQuery failedScanItemQuery;

    private GetScanSpaceStatsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetScanSpaceStatsUseCase(checkpointRepository, statsRepository, failedScanItemQuery);
    }

    private static ScanSpaceStats baseStats() {
        return new ScanSpaceStats(SCAN_ID, SPACE_KEY,
            Instant.parse("2026-06-07T10:00:00Z"), Instant.parse("2026-06-07T10:12:34Z"),
            42, 1, 1_200_000L, 7, 2, 530_000L, List.of(), List.of());
    }

    private static ScanCheckpoint checkpoint() {
        return ScanCheckpoint.builder().scanId(SCAN_ID).spaceKey(SPACE_KEY).build();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_SpaceKeyBlank")
    void Should_ReturnEmpty_When_SpaceKeyBlank() {
        assertThat(useCase.getLatestSpaceStats("  ")).isEmpty();
        verifyNoInteractions(checkpointRepository, statsRepository, failedScanItemQuery);
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_NoCheckpointForSpace")
    void Should_ReturnEmpty_When_NoCheckpointForSpace() {
        when(checkpointRepository.findLatestBySpace(SPACE_KEY)).thenReturn(Optional.empty());

        assertThat(useCase.getLatestSpaceStats(SPACE_KEY)).isEmpty();
        verifyNoInteractions(statsRepository, failedScanItemQuery);
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_NoStatsRowForResolvedScan")
    void Should_ReturnEmpty_When_NoStatsRowForResolvedScan() {
        when(checkpointRepository.findLatestBySpace(SPACE_KEY)).thenReturn(Optional.of(checkpoint()));
        when(statsRepository.findStats(SCAN_ID, SPACE_KEY)).thenReturn(Optional.empty());

        assertThat(useCase.getLatestSpaceStats(SPACE_KEY)).isEmpty();
        verifyNoInteractions(failedScanItemQuery);
    }

    @Test
    @DisplayName("Should_AggregateStatsDetectorsAndFailedItems_When_StatsExist")
    void Should_AggregateStatsDetectorsAndFailedItems_When_StatsExist() {
        when(checkpointRepository.findLatestBySpace(SPACE_KEY)).thenReturn(Optional.of(checkpoint()));
        when(statsRepository.findStats(SCAN_ID, SPACE_KEY)).thenReturn(Optional.of(baseStats()));
        List<ScanDetectorStat> detectors = List.of(new ScanDetectorStat("MINISTRAL", 12, 1_730_000L, 520_000L, 0, 0, null));
        when(statsRepository.findDetectorStats(SCAN_ID, SPACE_KEY)).thenReturn(detectors);
        List<FailedScanItem> failed = List.of(new FailedScanItem(ItemType.PAGE, "Broken page"));
        when(failedScanItemQuery.findFailedItems(SCAN_ID, SPACE_KEY, GetScanSpaceStatsUseCase.MAX_FAILED_ITEMS))
            .thenReturn(failed);

        ScanSpaceStats result = useCase.getLatestSpaceStats(SPACE_KEY).orElseThrow();

        assertThat(result.scanId()).isEqualTo(SCAN_ID);
        assertThat(result.pagesScanned()).isEqualTo(42);
        assertThat(result.detectorStats()).isEqualTo(detectors);
        assertThat(result.failedItems()).isEqualTo(failed);
        assertThat(result.durationMs()).isEqualTo(754_000L);
    }

    @Test
    @DisplayName("Should_CapFailedItemsRequest_When_QueryingFailedItems")
    void Should_CapFailedItemsRequest_When_QueryingFailedItems() {
        when(checkpointRepository.findLatestBySpace(SPACE_KEY)).thenReturn(Optional.of(checkpoint()));
        when(statsRepository.findStats(SCAN_ID, SPACE_KEY)).thenReturn(Optional.of(baseStats()));
        when(statsRepository.findDetectorStats(SCAN_ID, SPACE_KEY)).thenReturn(List.of());
        when(failedScanItemQuery.findFailedItems(SCAN_ID, SPACE_KEY, 20)).thenReturn(List.of());

        useCase.getLatestSpaceStats(SPACE_KEY);

        verify(failedScanItemQuery).findFailedItems(SCAN_ID, SPACE_KEY, 20);
    }
}
