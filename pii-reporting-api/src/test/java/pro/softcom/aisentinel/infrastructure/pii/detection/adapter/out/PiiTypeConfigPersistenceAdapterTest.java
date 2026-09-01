package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort.PiiTypeConfigUpdate;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity.PiiTypeConfigEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiTypeConfigJpaRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PiiTypeConfigPersistenceAdapterTest {

    @Mock
    private PiiTypeConfigJpaRepository jpaRepository;

    @InjectMocks
    private PiiTypeConfigPersistenceAdapter adapter;

    private PiiTypeConfigEntity buildEntity(String piiType, String detector) {
        PiiTypeConfig domain = PiiTypeConfig.builder()
                .piiType(piiType)
                .detector(detector)
                .enabled(true)
                .threshold(0.80)
                .category("CONTACT")
                .detectorLabel(piiType.toLowerCase())
                .severity("LOW")
                .build();
        return PiiTypeConfigEntity.fromDomain(domain);
    }

    @Test
    void Should_ReturnMappedList_When_FindAll() {
        when(jpaRepository.findAll()).thenReturn(List.of(buildEntity("EMAIL", "MINISTRAL")));

        List<PiiTypeConfig> result = adapter.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPiiType()).isEqualTo("EMAIL");
    }

    @Test
    void Should_ReturnEmptyList_When_FindAllReturnsNothing() {
        when(jpaRepository.findAll()).thenReturn(List.of());

        List<PiiTypeConfig> result = adapter.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnFilteredList_When_FindByDetector() {
        when(jpaRepository.findByDetector("PRESIDIO"))
                .thenReturn(List.of(buildEntity("CREDIT_CARD", "PRESIDIO")));

        List<PiiTypeConfig> result = adapter.findByDetector("PRESIDIO");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDetector()).isEqualTo("PRESIDIO");
    }

    @Test
    void Should_ReturnPresent_When_FindByPiiTypeAndDetectorExists() {
        when(jpaRepository.findByPiiTypeAndDetector("EMAIL", "MINISTRAL"))
                .thenReturn(Optional.of(buildEntity("EMAIL", "MINISTRAL")));

        Optional<PiiTypeConfig> result = adapter.findByPiiTypeAndDetector("EMAIL", "MINISTRAL");

        assertThat(result).isPresent();
        assertThat(result.get().getPiiType()).isEqualTo("EMAIL");
    }

    @Test
    void Should_ReturnEmpty_When_FindByPiiTypeAndDetectorNotFound() {
        when(jpaRepository.findByPiiTypeAndDetector("UNKNOWN", "MINISTRAL"))
                .thenReturn(Optional.empty());

        Optional<PiiTypeConfig> result = adapter.findByPiiTypeAndDetector("UNKNOWN", "MINISTRAL");

        assertThat(result).isEmpty();
    }

    @Test
    void Should_ReturnTrue_When_ExistsAndCountIsPositive() {
        when(jpaRepository.count()).thenReturn(5L);

        boolean result = adapter.exists();

        assertThat(result).isTrue();
    }

    @Test
    void Should_ReturnFalse_When_ExistsAndCountIsZero() {
        when(jpaRepository.count()).thenReturn(0L);

        boolean result = adapter.exists();

        assertThat(result).isFalse();
    }

    @Test
    void Should_ThrowIllegalArgument_When_UpdateAtomicallyNotFound() {
        when(jpaRepository.findByPiiTypeAndDetector("UNKNOWN", "MINISTRAL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.updateAtomically(
                "UNKNOWN", "MINISTRAL", true, 0.80, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration not found");
    }

    @Test
    void Should_ReturnUpdated_When_UpdateAtomicallySucceeds() {
        PiiTypeConfigEntity entity = buildEntity("EMAIL", "MINISTRAL");
        when(jpaRepository.findByPiiTypeAndDetector("EMAIL", "MINISTRAL"))
                .thenReturn(Optional.of(entity));
        when(jpaRepository.save(entity)).thenReturn(entity);

        PiiTypeConfig result = adapter.updateAtomically("EMAIL", "MINISTRAL", false, 0.90, "admin");

        assertThat(result).isNotNull();
    }

    @Test
    void Should_ReturnBulkUpdated_When_BulkUpdateAtomically() {
        PiiTypeConfigEntity entity = buildEntity("EMAIL", "MINISTRAL");
        when(jpaRepository.findByPiiTypeAndDetector("EMAIL", "MINISTRAL"))
                .thenReturn(Optional.of(entity));
        when(jpaRepository.saveAll(anyList())).thenReturn(List.of(entity));

        List<PiiTypeConfigUpdate> updates = List.of(
                new PiiTypeConfigUpdate("EMAIL", "MINISTRAL", true, 0.85)
        );

        List<PiiTypeConfig> result = adapter.bulkUpdateAtomically(updates, "admin");

        assertThat(result).hasSize(1);
    }
}
