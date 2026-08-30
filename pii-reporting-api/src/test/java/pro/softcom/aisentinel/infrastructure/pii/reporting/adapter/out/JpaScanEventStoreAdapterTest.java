package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaScanEventStoreAdapterTest {

    @Mock
    private DetectionEventRepository eventRepository;
    @Mock
    private ScanResultEncryptor scanResultEncryptor;
    @Mock
    private PiiContextExtractor piiContextExtractor;

    private JpaScanEventStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaScanEventStoreAdapter(
                eventRepository, scanResultEncryptor, piiContextExtractor, new ObjectMapper()
        );
    }

    private ConfluenceContentScanResult buildResult(String scanId, String eventType) {
        return ConfluenceContentScanResult.builder()
                .scanId(scanId)
                .spaceKey("SPACE")
                .eventType(eventType)
                .pageId("PAGE-1")
                .pageTitle("My Page")
                .build();
    }

    @Test
    void Should_SaveEvent_When_ValidScanResultAppended() {
        // Arrange
        String scanId = "scan-1";
        ConfluenceContentScanResult result = buildResult(scanId, "PAGE");
        when(scanResultEncryptor.encrypt(result)).thenReturn(result);
        when(eventRepository.findMaxEventSeqByScanId(scanId)).thenReturn(0L);
        ArgumentCaptor<ScanEventEntity> captor = ArgumentCaptor.forClass(ScanEventEntity.class);

        // Act
        adapter.append(result);

        // Assert
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getScanId()).isEqualTo(scanId);
        assertThat(captor.getValue().getEventType()).isEqualTo("PAGE");
        assertThat(captor.getValue().getEventSeq()).isEqualTo(1L);
    }

    @Test
    void Should_NotSave_When_ScanResultIsNull() {
        adapter.append(null);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void Should_NotSave_When_ScanIdIsBlank() {
        ConfluenceContentScanResult result = ConfluenceContentScanResult.builder()
                .scanId("")
                .eventType("PAGE")
                .spaceKey("SPACE")
                .build();

        adapter.append(result);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void Should_NotSave_When_EventTypeIsBlank() {
        ConfluenceContentScanResult result = ConfluenceContentScanResult.builder()
                .scanId("scan-blank-type")
                .eventType("")
                .spaceKey("SPACE")
                .build();

        adapter.append(result);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void Should_IncrementSequence_When_MultipleEventsAppended() {
        // Arrange
        String scanId = "scan-seq";
        ConfluenceContentScanResult result = buildResult(scanId, "PAGE");
        when(scanResultEncryptor.encrypt(result)).thenReturn(result);
        when(eventRepository.findMaxEventSeqByScanId(scanId)).thenReturn(0L);
        ArgumentCaptor<ScanEventEntity> captor = ArgumentCaptor.forClass(ScanEventEntity.class);

        // Act
        adapter.append(result);
        adapter.append(result);

        // Assert
        verify(eventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getEventSeq()).isEqualTo(1L);
        assertThat(captor.getAllValues().get(1).getEventSeq()).isEqualTo(2L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-01-01T10:00:00Z", "not-a-date"})
    void Should_SetTimestamp_When_AppendingResult(String emittedAt) {
        String scanId = "scan-ts";
        ConfluenceContentScanResult result = ConfluenceContentScanResult.builder()
                .scanId(scanId)
                .spaceKey("SPACE")
                .eventType("PAGE")
                .emittedAt(emittedAt)
                .build();
        when(scanResultEncryptor.encrypt(result)).thenReturn(result);
        when(eventRepository.findMaxEventSeqByScanId(scanId)).thenReturn(0L);
        ArgumentCaptor<ScanEventEntity> captor = ArgumentCaptor.forClass(ScanEventEntity.class);

        adapter.append(result);

        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    void Should_CallDeleteAllInBatch_When_DeleteAllCalled() {
        adapter.deleteAll();

        verify(eventRepository).deleteAllInBatch();
    }

    @Test
    void Should_RecoverGracefully_When_RepositoryThrows() {
        String scanId = "scan-error";
        ConfluenceContentScanResult result = buildResult(scanId, "PAGE");
        when(scanResultEncryptor.encrypt(result)).thenReturn(result);
        when(eventRepository.findMaxEventSeqByScanId(anyString())).thenReturn(0L);
        when(eventRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatCode(() -> adapter.append(result)).doesNotThrowAnyException();
    }
}
