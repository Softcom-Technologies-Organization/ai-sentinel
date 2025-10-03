package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;

@ExtendWith(MockitoExtension.class)
class ScanCheckpointPersistenceAdapterTest {

    @Mock
    private DetectionCheckpointRepository jpaRepository;

    private ScanCheckpointPersistenceAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new ScanCheckpointPersistenceAdapter(jpaRepository);
    }

    @Test
    void save_should_ignore_null_checkpoint() {
        repository.save(null);
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void save_should_ignore_when_scan_or_space_is_blank() {
        // blank scan id
        var cp1 = ScanCheckpoint.builder().scanId(" ").spaceKey("SPACE").build();
        repository.save(cp1);
        // blank space key
        var cp2 = ScanCheckpoint.builder().scanId("scan").spaceKey(" ").build();
        repository.save(cp2);
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void save_should_persist_entity_mirrored_from_checkpoint() {
        var now = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        var cp = ScanCheckpoint.builder()
                .scanId("scan-1")
                .spaceKey("SPACE")
                .lastProcessedPageId("123")
                .lastProcessedAttachmentName("file.txt")
                .scanStatus(ScanStatus.COMPLETED)
                .updatedAt(now)
                .build();

        repository.save(cp);

        ArgumentCaptor<ScanCheckpointEntity> captor = ArgumentCaptor.forClass(ScanCheckpointEntity.class);
        verify(jpaRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getScanId()).isEqualTo("scan-1");
        assertThat(saved.getSpaceKey()).isEqualTo("SPACE");
        assertThat(saved.getLastProcessedPageId()).isEqualTo("123");
        assertThat(saved.getLastProcessedAttachmentName()).isEqualTo("file.txt");
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void findByScanAndSpace_should_return_empty_on_blank_inputs() {
        assertThat(repository.findByScanAndSpace(null, "SPACE")).isEmpty();
        assertThat(repository.findByScanAndSpace(" ", "SPACE")).isEmpty();
        assertThat(repository.findByScanAndSpace("scan", null)).isEmpty();
        assertThat(repository.findByScanAndSpace("scan", " ")).isEmpty();
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findByScanAndSpace_should_map_entity_to_domain_and_default_invalid_status() {
        var ts = LocalDateTime.of(2024, 1, 2, 10, 0, 0);
        //use builder
        var entity = ScanCheckpointEntity.builder().scanId("scan-2").spaceKey("SPACE").lastProcessedPageId("p-1").lastProcessedAttachmentName("a-1").status("NOT_A_STATUS").updatedAt(ts).build();
        when(jpaRepository.findByScanIdAndSpaceKey("scan-2", "SPACE"))
                .thenReturn(Optional.of(entity));

        var result = repository.findByScanAndSpace("scan-2", "SPACE");
        assertThat(result).isPresent();
        var cp = result.orElseThrow();
        assertThat(cp.scanId()).isEqualTo("scan-2");
        assertThat(cp.spaceKey()).isEqualTo("SPACE");
        assertThat(cp.lastProcessedPageId()).isEqualTo("p-1");
        assertThat(cp.lastProcessedAttachmentName()).isEqualTo("a-1");
        // invalid status must fallback to RUNNING
        assertThat(cp.scanStatus()).isEqualTo(ScanStatus.RUNNING);
        assertThat(cp.updatedAt()).isEqualTo(ts);
    }

    @Test
    void findByScan_should_return_empty_list_on_blank_scan() {
        assertThat(repository.findByScan(null)).isEmpty();
        assertThat(repository.findByScan(" ")).isEmpty();
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void findByScan_should_map_entities_to_domain() {
        //use builder
        var e1 = ScanCheckpointEntity.builder().scanId("s").spaceKey("A").status("COMPLETED").updatedAt(LocalDateTime.now()).build();
        var e2 = ScanCheckpointEntity.builder().scanId("s").spaceKey("B").status("FAILED").updatedAt(LocalDateTime.now()).build();
        when(jpaRepository.findByScanIdOrderBySpaceKey("s")).thenReturn(List.of(e1, e2));

        var list = repository.findByScan("s");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).spaceKey()).isEqualTo("A");
        assertThat(list.get(0).scanStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(list.get(1).spaceKey()).isEqualTo("B");
        assertThat(list.get(1).scanStatus()).isEqualTo(ScanStatus.FAILED);
    }

    @Test
    void deleteByScan_should_ignore_blank_and_call_delete_for_valid() {
        repository.deleteByScan(null);
        repository.deleteByScan(" ");
        verifyNoInteractions(jpaRepository);

        repository.deleteByScan("scan-x");
        verify(jpaRepository).deleteByScanId("scan-x");
        verifyNoMoreInteractions(jpaRepository);
    }
}
