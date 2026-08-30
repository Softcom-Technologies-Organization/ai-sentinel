package pro.softcom.aisentinel.domain.pii.reporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanDetectorStatTest {

    @Test
    @DisplayName("Should_ComputeThroughputRoundedToOneDecimal_When_BusyTimePositive")
    void Should_ComputeThroughputRoundedToOneDecimal_When_BusyTimePositive() {
        ScanDetectorStat stat = new ScanDetectorStat("MINISTRAL", 12, 1_730_000L, 520_000L, 0, 0, null);

        assertThat(stat.charsPerSecond()).isEqualTo(3326.9);
    }

    @Test
    @DisplayName("Should_ReturnNullThroughput_When_BusyTimeIsZero")
    void Should_ReturnNullThroughput_When_BusyTimeIsZero() {
        ScanDetectorStat stat = new ScanDetectorStat("REGEX", 0, 5_000L, 0L, 0, 0, null);

        assertThat(stat.charsPerSecond()).isNull();
    }

    @Test
    @DisplayName("Should_RoundHalfUp_When_ThroughputHasTrailingDecimals")
    void Should_RoundHalfUp_When_ThroughputHasTrailingDecimals() {
        // 1000 chars over 3000 ms -> 333.333... -> 333.3
        ScanDetectorStat stat = new ScanDetectorStat("PRESIDIO", 1, 1_000L, 3_000L, 0, 0, null);

        assertThat(stat.charsPerSecond()).isEqualTo(333.3);
    }

    @Test
    @DisplayName("Should_CarryDiscardedCount_When_PostFilterStat")
    void Should_CarryDiscardedCount_When_PostFilterStat() {
        ScanDetectorStat postfilter = new ScanDetectorStat("POSTFILTER", 420, 0L, 1_400L, 18, 0, null);

        assertThat(postfilter.discarded()).isEqualTo(18);
    }

    @Test
    @DisplayName("Should_CarryFailureCountAndReason_When_DetectorCouldNotRun")
    void Should_CarryFailureCountAndReason_When_DetectorCouldNotRun() {
        ScanDetectorStat unreachable = new ScanDetectorStat(
            "MINISTRAL", 0, 12_000L, 90_000L, 0, 7, "ConnectError: refused");

        assertThat(unreachable.failedRequests()).isEqualTo(7);
        assertThat(unreachable.lastError()).isEqualTo("ConnectError: refused");
        // Zero detections plus a failure count is what distinguishes a dead detector
        // from a clean document.
        assertThat(unreachable.detections()).isZero();
    }
}
