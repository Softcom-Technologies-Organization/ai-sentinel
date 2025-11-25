package pro.softcom.aisentinel.application.pii.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSeverityCountRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSeverityCount;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;

@ExtendWith(MockitoExtension.class)
class ScanSeverityCountServiceTest {

    @Mock
    private ScanSeverityCountRepository repository;

    @InjectMocks
    private ScanSeverityCountService service;

    @Nested
    class IncrementCounts {

        @Test
        void Should_DelegateToRepository_When_ValidInput() {
            String scanId = "scan-123";
            String spaceKey = "SPACE-A";
            SeverityCounts delta = new SeverityCounts(5, 3, 2);

            service.incrementCounts(scanId, spaceKey, delta);

            verify(repository).incrementCounts(scanId, spaceKey, delta);
        }

        @Test
        void Should_ThrowException_When_ScanIdIsNull() {
            SeverityCounts delta = new SeverityCounts(1, 1, 1);

            assertThatThrownBy(() -> service.incrementCounts(null, "SPACE-A", delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_ScanIdIsBlank() {
            SeverityCounts delta = new SeverityCounts(1, 1, 1);

            assertThatThrownBy(() -> service.incrementCounts("  ", "SPACE-A", delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_SpaceKeyIsNull() {
            SeverityCounts delta = new SeverityCounts(1, 1, 1);

            assertThatThrownBy(() -> service.incrementCounts("scan-123", null, delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("spaceKey must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_SpaceKeyIsBlank() {
            SeverityCounts delta = new SeverityCounts(1, 1, 1);

            assertThatThrownBy(() -> service.incrementCounts("scan-123", "", delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("spaceKey must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_DeltaIsNull() {
            assertThatThrownBy(() -> service.incrementCounts("scan-123", "SPACE-A", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delta must not be null");
        }
    }

    @Nested
    class GetCounts {

        @Test
        void Should_ReturnCounts_When_RecordExists() {
            String scanId = "scan-123";
            String spaceKey = "SPACE-A";
            SeverityCounts expected = new SeverityCounts(10, 5, 3);
            when(repository.findByScanIdAndSpaceKey(scanId, spaceKey))
                .thenReturn(Optional.of(expected));

            Optional<SeverityCounts> result = service.getCounts(scanId, spaceKey);

            assertThat(result).isPresent().contains(expected);
            verify(repository).findByScanIdAndSpaceKey(scanId, spaceKey);
        }

        @Test
        void Should_ReturnEmpty_When_RecordDoesNotExist() {
            String scanId = "scan-123";
            String spaceKey = "SPACE-A";
            when(repository.findByScanIdAndSpaceKey(scanId, spaceKey))
                .thenReturn(Optional.empty());

            Optional<SeverityCounts> result = service.getCounts(scanId, spaceKey);

            assertThat(result).isEmpty();
            verify(repository).findByScanIdAndSpaceKey(scanId, spaceKey);
        }

        @Test
        void Should_ThrowException_When_ScanIdIsNull() {
            assertThatThrownBy(() -> service.getCounts(null, "SPACE-A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_SpaceKeyIsNull() {
            assertThatThrownBy(() -> service.getCounts("scan-123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("spaceKey must not be null or blank");
        }
    }

    @Nested
    class GetCountsByScan {

        @Test
        void Should_ReturnListOfCounts_When_ScanHasData() {
            String scanId = "scan-123";
            List<ScanSeverityCount> expected = List.of(
                new ScanSeverityCount(scanId, "SPACE-A", new SeverityCounts(5, 3, 2)),
                new ScanSeverityCount(scanId, "SPACE-B", new SeverityCounts(8, 4, 1))
            );
            when(repository.findByScanId(scanId)).thenReturn(expected);

            List<ScanSeverityCount> result = service.getCountsByScan(scanId);

            assertThat(result).hasSize(2).isEqualTo(expected);
            verify(repository).findByScanId(scanId);
        }

        @Test
        void Should_ReturnEmptyList_When_ScanHasNoData() {
            String scanId = "scan-123";
            when(repository.findByScanId(scanId)).thenReturn(List.of());

            List<ScanSeverityCount> result = service.getCountsByScan(scanId);

            assertThat(result).isEmpty();
            verify(repository).findByScanId(scanId);
        }

        @Test
        void Should_ThrowException_When_ScanIdIsNull() {
            assertThatThrownBy(() -> service.getCountsByScan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_ScanIdIsBlank() {
            assertThatThrownBy(() -> service.getCountsByScan(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }
    }

    @Nested
    class DeleteCounts {

        @Test
        void Should_DelegateToRepository_When_ValidScanId() {
            String scanId = "scan-123";

            service.deleteCounts(scanId);

            verify(repository).deleteByScanId(scanId);
        }

        @Test
        void Should_ThrowException_When_ScanIdIsNull() {
            assertThatThrownBy(() -> service.deleteCounts(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }

        @Test
        void Should_ThrowException_When_ScanIdIsBlank() {
            assertThatThrownBy(() -> service.deleteCounts("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scanId must not be null or blank");
        }
    }
}
