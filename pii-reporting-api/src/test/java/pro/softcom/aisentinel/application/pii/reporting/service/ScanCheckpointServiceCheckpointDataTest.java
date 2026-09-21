package pro.softcom.aisentinel.application.pii.reporting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ScanCheckpointService checkpoint data extraction (event types).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScanCheckpointService - checkpoint data extraction")
class ScanCheckpointServiceCheckpointDataTest {

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @InjectMocks
    private ScanCheckpointService scanCheckpointService;

    private ConfluenceContentScanResult event(String eventType, String pageId, String attachmentName) {
        return ConfluenceContentScanResult.builder()
                .scanId("scan-1")
                .spaceKey("SPACE")
                .eventType(eventType)
                .pageId(pageId)
                .attachmentName(attachmentName)
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"NOT_STARTED", "RUNNING"})
    void Should_MarkCheckpointRunningWithoutAdvancingCursor_When_SpaceStarts(ScanStatus existingStatus) {
        var existing = ScanCheckpoint.builder().scanId("scan-1").spaceKey("SPACE")
            .scanStatus(existingStatus).lastProcessedPageId("previous-page").build();
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE"))
            .thenReturn(Optional.of(existing));

        scanCheckpointService.persistCheckpoint(event("start", "next-page", "next-attachment"));

        ArgumentCaptor<ScanCheckpoint> captor = ArgumentCaptor.forClass(ScanCheckpoint.class);
        verify(scanCheckpointRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().scanStatus()).isEqualTo(ScanStatus.RUNNING);
            softly.assertThat(captor.getValue().lastProcessedPageId()).isNull();
            softly.assertThat(captor.getValue().lastProcessedAttachmentName()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(value = ScanStatus.class, names = {"PAUSED", "COMPLETED"})
    void Should_PreserveCheckpointStatus_When_StartEventArrivesAfterPauseOrCompletion(ScanStatus existingStatus) {
        var existing = ScanCheckpoint.builder().scanId("scan-1").spaceKey("SPACE")
            .scanStatus(existingStatus).build();
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE"))
            .thenReturn(Optional.of(existing));

        scanCheckpointService.persistCheckpoint(event("start", null, null));

        verify(scanCheckpointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_SaveCheckpointWithRunningStatus_When_EventTypeIsItem")
    void Should_SaveCheckpointWithRunningStatus_When_EventTypeIsItem() {
        // Arrange
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE")).thenReturn(Optional.empty());
        ConfluenceContentScanResult itemEvent = event("item", "p1", null);

        // Act
        scanCheckpointService.persistCheckpoint(itemEvent);

        // Assert
        ArgumentCaptor<ScanCheckpoint> captor = ArgumentCaptor.forClass(ScanCheckpoint.class);
        verify(scanCheckpointRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().scanStatus()).isEqualTo(ScanStatus.RUNNING);
            softly.assertThat(captor.getValue().lastProcessedPageId()).isNull();
            softly.assertThat(captor.getValue().lastProcessedAttachmentName()).isNull();
        });
    }

    @Test
    @DisplayName("Should_SaveCheckpointWithAttachmentName_When_EventTypeIsAttachmentItem")
    void Should_SaveCheckpointWithAttachmentName_When_EventTypeIsAttachmentItem() {
        // Arrange
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE")).thenReturn(Optional.empty());
        ConfluenceContentScanResult attachEvent = event("attachmentItem", "p1", "file.pdf");

        // Act
        scanCheckpointService.persistCheckpoint(attachEvent);

        // Assert
        ArgumentCaptor<ScanCheckpoint> captor = ArgumentCaptor.forClass(ScanCheckpoint.class);
        verify(scanCheckpointRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().scanStatus()).isEqualTo(ScanStatus.RUNNING);
            softly.assertThat(captor.getValue().lastProcessedAttachmentName()).isEqualTo("file.pdf");
            softly.assertThat(captor.getValue().lastProcessedPageId()).isNull();
        });
    }

    @Test
    @DisplayName("Should_SaveCheckpointWithPageId_When_EventTypeIsPageComplete")
    void Should_SaveCheckpointWithPageId_When_EventTypeIsPageComplete() {
        // Arrange
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE")).thenReturn(Optional.empty());
        ConfluenceContentScanResult pageComplete = event("pageComplete", "page-42", null);

        // Act
        scanCheckpointService.persistCheckpoint(pageComplete);

        // Assert
        ArgumentCaptor<ScanCheckpoint> captor = ArgumentCaptor.forClass(ScanCheckpoint.class);
        verify(scanCheckpointRepository).save(captor.capture());
        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().scanStatus()).isEqualTo(ScanStatus.RUNNING);
            softly.assertThat(captor.getValue().lastProcessedPageId()).isEqualTo("page-42");
        });
    }

    @Test
    @DisplayName("Should_SaveCheckpointWithCompletedStatus_When_EventTypeIsComplete")
    void Should_SaveCheckpointWithCompletedStatus_When_EventTypeIsComplete() {
        // Arrange
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE")).thenReturn(Optional.empty());
        ConfluenceContentScanResult completeEvent = event("complete", null, null);

        // Act
        scanCheckpointService.persistCheckpoint(completeEvent);

        // Assert
        ArgumentCaptor<ScanCheckpoint> captor = ArgumentCaptor.forClass(ScanCheckpoint.class);
        verify(scanCheckpointRepository).save(captor.capture());
        assertThat(captor.getValue().scanStatus()).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should_NotSave_When_EventTypeIsUnknown")
    void Should_NotSave_When_EventTypeIsUnknown() {
        // Arrange
        when(scanCheckpointRepository.findByScanAndSpace("scan-1", "SPACE")).thenReturn(Optional.empty());
        ConfluenceContentScanResult unknownEvent = event("unknown-event", null, null);

        // Act
        scanCheckpointService.persistCheckpoint(unknownEvent);

        // Assert - null data → skip save
        verify(scanCheckpointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_NotSave_When_EventTypeIsNull")
    void Should_NotSave_When_EventTypeIsNull() {
        // Arrange
        ConfluenceContentScanResult nullTypeEvent = event(null, null, null);

        // Act
        scanCheckpointService.persistCheckpoint(nullTypeEvent);

        // Assert
        verify(scanCheckpointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_NotPersist_When_EventIsNull")
    void Should_NotPersist_When_EventIsNull() {
        // Act
        scanCheckpointService.persistCheckpoint(null);

        // Assert
        verify(scanCheckpointRepository, never()).findByScanAndSpace(anyString(), anyString());
        verify(scanCheckpointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_NotPersist_When_ScanIdIsBlank")
    void Should_NotPersist_When_ScanIdIsBlank() {
        // Arrange
        ConfluenceContentScanResult blankScanId = ConfluenceContentScanResult.builder()
                .scanId("   ")
                .spaceKey("SPACE")
                .eventType("item")
                .build();

        // Act
        scanCheckpointService.persistCheckpoint(blankScanId);

        // Assert
        verify(scanCheckpointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_NotPersist_When_SpaceKeyIsBlank")
    void Should_NotPersist_When_SpaceKeyIsBlank() {
        // Arrange
        ConfluenceContentScanResult blankSpaceKey = ConfluenceContentScanResult.builder()
                .scanId("scan-1")
                .spaceKey("")
                .eventType("item")
                .build();

        // Act
        scanCheckpointService.persistCheckpoint(blankSpaceKey);

        // Assert
        verify(scanCheckpointRepository, never()).save(any());
    }
}
