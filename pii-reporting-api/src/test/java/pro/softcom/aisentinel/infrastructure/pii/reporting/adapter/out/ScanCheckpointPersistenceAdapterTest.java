package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanCheckpointPersistenceAdapterTest {

    @Mock
    private DetectionCheckpointRepository jpaRepository;

    @InjectMocks
    private ScanCheckpointPersistenceAdapter adapter;

    @Test
    void Should_PersistProgressPercentage_When_SavingCheckpoint() {
        // Given
        Double expectedProgress = 75.5;
        ScanCheckpoint checkpoint = ScanCheckpoint.builder()
            .scanId("scan-123")
            .spaceKey("SPACE")
            .lastProcessedPageId("page-1")
            .lastProcessedAttachmentName("attachment-1")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(expectedProgress)
            .updatedAt(LocalDateTime.now())
            .build();

        // When
        adapter.save(checkpoint);

        // Then
        verify(jpaRepository).upsertCheckpoint(
            eq("scan-123"),
            eq("SPACE"),
            eq("page-1"),
            eq("attachment-1"),
            eq("RUNNING"),
            eq(expectedProgress),
            any(LocalDateTime.class)
        );
    }

    @Test
    void Should_ReturnProgressPercentage_When_MappingEntityToDomain() {
        // Given
        Double expectedProgress = 42.8;
        ScanCheckpointEntity entity = ScanCheckpointEntity.builder()
            .scanId("scan-456")
            .spaceKey("MYSPACE")
            .lastProcessedPageId("page-5")
            .lastProcessedAttachmentName("file.pdf")
            .status("COMPLETED")
            .progressPercentage(expectedProgress)
            .updatedAt(LocalDateTime.now())
            .build();

        when(jpaRepository.findByScanIdAndSpaceKey("scan-456", "MYSPACE"))
            .thenReturn(Optional.of(entity));

        // When
        Optional<ScanCheckpoint> result = adapter.findByScanAndSpace("scan-456", "MYSPACE");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().progressPercentage()).isEqualTo(expectedProgress);
    }

    @Test
    void Should_HandleNullProgressPercentage_When_MappingEntityToDomain() {
        // Given
        ScanCheckpointEntity entity = ScanCheckpointEntity.builder()
            .scanId("scan-789")
            .spaceKey("TESTSPACE")
            .lastProcessedPageId("page-10")
            .status("RUNNING")
            .progressPercentage(null)
            .updatedAt(LocalDateTime.now())
            .build();

        when(jpaRepository.findByScanIdAndSpaceKey("scan-789", "TESTSPACE"))
            .thenReturn(Optional.of(entity));

        // When
        Optional<ScanCheckpoint> result = adapter.findByScanAndSpace("scan-789", "TESTSPACE");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().progressPercentage()).isNull();
    }

    @Test
    void Should_NotCallUpsert_When_CheckpointIsNull() {
        // When
        adapter.save(null);

        // Then
        verify(jpaRepository, never()).upsertCheckpoint(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(LocalDateTime.class)
        );
    }

    @Test
    void Should_NotCallUpsert_When_ScanIdIsBlank() {
        // Given
        ScanCheckpoint checkpoint = ScanCheckpoint.builder()
            .scanId("")
            .spaceKey("SPACE")
            .scanStatus(ScanStatus.RUNNING)
            .progressPercentage(50.0)
            .build();

        // When
        adapter.save(checkpoint);

        // Then
        verify(jpaRepository, never()).upsertCheckpoint(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(LocalDateTime.class)
        );
    }

    @Test
    void Should_ReturnEmpty_When_FindByScanAndSpaceWithBlankScanId() {
        Optional<ScanCheckpoint> result = adapter.findByScanAndSpace("", "SPACE");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnEmpty_When_FindByScanAndSpaceWithBlankSpaceKey() {
        Optional<ScanCheckpoint> result = adapter.findByScanAndSpace("scan-1", "");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnEmptyList_When_FindByScanWithBlankScanId() {
        List<ScanCheckpoint> result = adapter.findByScan("");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnList_When_FindByScanWithValidScanId() {
        ScanCheckpointEntity entity = ScanCheckpointEntity.builder()
            .scanId("scan-1")
            .spaceKey("SPACE")
            .status("RUNNING")
            .progressPercentage(50.0)
            .updatedAt(LocalDateTime.now())
            .build();
        when(jpaRepository.findByScanIdOrderBySpaceKey("scan-1")).thenReturn(List.of(entity));

        List<ScanCheckpoint> result = adapter.findByScan("scan-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().scanId()).isEqualTo("scan-1");
    }

    @Test
    void Should_ReturnEmptyList_When_FindBySpaceWithBlankSpaceKey() {
        List<ScanCheckpoint> result = adapter.findBySpace("  ");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnEmpty_When_FindLatestBySpaceWithBlankKey() {
        Optional<ScanCheckpoint> result = adapter.findLatestBySpace("");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_CallDelete_When_DeleteByScanWithValidId() {
        adapter.deleteByScan("scan-del");

        verify(jpaRepository).deleteByScanId("scan-del");
    }

    @Test
    void Should_NotCallDelete_When_DeleteByScanWithBlankId() {
        adapter.deleteByScan("");

        verify(jpaRepository, never()).deleteByScanId(anyString());
    }

    @Test
    void Should_ReturnZero_When_PauseUnfinishedCheckpointsWithBlankScanId() {
        int result = adapter.pauseUnfinishedCheckpoints("");

        assertThat(result).isZero();
    }

    @Test
    void Should_DelegateAndReturnCount_When_PauseUnfinishedCheckpoints() {
        when(jpaRepository.pauseUnfinishedCheckpoints("scan-1")).thenReturn(3);

        int result = adapter.pauseUnfinishedCheckpoints("scan-1");

        assertThat(result).isEqualTo(3);
    }

    @Test
    void Should_ReturnZero_When_ResumeAllPausedCheckpointsWithBlankScanId() {
        int result = adapter.resumeAllPausedCheckpoints("  ");

        assertThat(result).isZero();
    }

    @Test
    void Should_ReturnZero_When_ResolveStaleActiveCheckpointsWithEmptyList() {
        int result = adapter.resolveStaleActiveCheckpoints(List.of());

        assertThat(result).isZero();
    }

    @Test
    void Should_ReturnZero_When_DeleteAllCheckpointsForSpacesWithEmptyList() {
        adapter.deleteAllCheckpointsForSpaces(List.of());

        verify(jpaRepository, never()).deleteAllCheckpointsForSpaces(any());
    }
}
