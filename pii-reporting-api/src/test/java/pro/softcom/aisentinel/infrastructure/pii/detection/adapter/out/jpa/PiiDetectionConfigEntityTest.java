package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectionConfigEntityTest {

    @Test
    void Should_KeepTrioOptIn_When_LegacyBuilderOmitsNewFields() {
        PiiDetectionConfigEntity entity = PiiDetectionConfigEntity.builder().id(1).build();

        assertThat(entity.getTrioEnabled()).isFalse();
        assertThat(entity.getBardsEnabled()).isTrue();
        assertThat(entity.getCbxEnabled()).isTrue();
        assertThat(entity.getEttinEnabled()).isTrue();
    }
}
