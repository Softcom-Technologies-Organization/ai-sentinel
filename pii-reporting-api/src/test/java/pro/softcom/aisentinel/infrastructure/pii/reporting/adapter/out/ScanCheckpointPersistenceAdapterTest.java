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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        verify(jpaRepository, org.mockito.Mockito.never()).upsertCheckpoint(
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
        verify(jpaRepository, org.mockito.Mockito.never()).upsertCheckpoint(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(LocalDateTime.class)
        );
    }
}
