package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanDetectorStatsJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanSpaceStatsJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsId;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSpaceStatsEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSpaceStatsId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanSpaceStatsPersistenceAdapterTest {

    private static final String SCAN_ID = "scan-1";
    private static final String SPACE_KEY = "KEY";

    @Mock
    private ScanSpaceStatsJpaRepository spaceStatsRepository;

    @Mock
    private ScanDetectorStatsJpaRepository detectorStatsRepository;

    private ScanSpaceStatsPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ScanSpaceStatsPersistenceAdapter(spaceStatsRepository, detectorStatsRepository);
    }

    @Test
    @DisplayName("Should_DelegatePageIncrement_When_IncrementPageScanned")
    void Should_DelegatePageIncrement_When_IncrementPageScanned() {
        adapter.incrementPageScanned(SCAN_ID, SPACE_KEY, 1234L);

        verify(spaceStatsRepository).incrementPageScanned(SCAN_ID, SPACE_KEY, 1234L);
    }

    @Test
    @DisplayName("Should_DelegateDetectorAccumulation_When_AccumulateDetectorStat")
    void Should_DelegateDetectorAccumulation_When_AccumulateDetectorStat() {
        ScanDetectorStatDelta delta =
            new ScanDetectorStatDelta("MINISTRAL", 520L, 1000L, 12, 0, 1, "ConnectError: refused");

        adapter.accumulateDetectorStat(SCAN_ID, SPACE_KEY, delta);

        verify(detectorStatsRepository).accumulate(SCAN_ID, SPACE_KEY, delta);
    }

    @Test
    @DisplayName("Should_MapEntityToDomain_When_StatsRowExists")
    void Should_MapEntityToDomain_When_StatsRowExists() {
        ScanSpaceStatsEntity entity = ScanSpaceStatsEntity.builder()
            .id(ScanSpaceStatsId.builder().scanId(SCAN_ID).spaceKey(SPACE_KEY).build())
            .startedAt(Instant.parse("2026-06-07T10:00:00Z"))
            .finishedAt(Instant.parse("2026-06-07T10:12:34Z"))
            .pagesScanned(42).pagesFailed(1).pageChars(1_200_000L)
            .attachmentsScanned(7).attachmentsFailed(2).attachmentChars(530_000L)
            .updatedAt(Instant.now())
            .build();
        when(spaceStatsRepository.findById(
            ScanSpaceStatsId.builder().scanId(SCAN_ID).spaceKey(SPACE_KEY).build()))
            .thenReturn(Optional.of(entity));

        ScanSpaceStats stats = adapter.findStats(SCAN_ID, SPACE_KEY).orElseThrow();

        assertThat(stats.scanId()).isEqualTo(SCAN_ID);
        assertThat(stats.spaceKey()).isEqualTo(SPACE_KEY);
        assertThat(stats.pagesScanned()).isEqualTo(42);
        assertThat(stats.attachmentChars()).isEqualTo(530_000L);
        assertThat(stats.durationMs()).isEqualTo(754_000L);
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_NoStatsRow")
    void Should_ReturnEmpty_When_NoStatsRow() {
        when(spaceStatsRepository.findById(
            ScanSpaceStatsId.builder().scanId(SCAN_ID).spaceKey(SPACE_KEY).build()))
            .thenReturn(Optional.empty());

        assertThat(adapter.findStats(SCAN_ID, SPACE_KEY)).isEmpty();
    }

    @Test
    @DisplayName("Should_MapDetectorEntitiesToDomain_When_FindingDetectorStats")
    void Should_MapDetectorEntitiesToDomain_When_FindingDetectorStats() {
        ScanDetectorStatsEntity entity = ScanDetectorStatsEntity.builder()
            .id(ScanDetectorStatsId.builder().scanId(SCAN_ID).spaceKey(SPACE_KEY).detector("REGEX").build())
            .busyMs(3L).charsProcessed(1000L).detections(0).discarded(0)
            .failedRequests(2).lastError("ConnectError: refused")
            .updatedAt(Instant.now())
            .build();
        when(detectorStatsRepository.findById_ScanIdAndId_SpaceKeyOrderById_Detector(SCAN_ID, SPACE_KEY))
            .thenReturn(List.of(entity));

        List<ScanDetectorStat> stats = adapter.findDetectorStats(SCAN_ID, SPACE_KEY);

        assertThat(stats).containsExactly(
            new ScanDetectorStat("REGEX", 0, 1000L, 3L, 0, 2, "ConnectError: refused"));
    }
}
