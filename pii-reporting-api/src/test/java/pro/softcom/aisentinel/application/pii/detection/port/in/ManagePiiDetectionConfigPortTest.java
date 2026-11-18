package pro.softcom.aisentinel.application.pii.detection.port.in;

import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort.UpdatePiiDetectionConfigCommand;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManagePiiDetectionConfigUseCase;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.application.pii.judge.service.JudgeCategoryPolicy;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagePiiDetectionConfigPortTest {

    @Test
    void Should_KeepChildChoices_When_TrioDisabledThroughPort() {
        PiiDetectionConfigRepository repository = mock(PiiDetectionConfigRepository.class);
        when(repository.findConfig()).thenReturn(mock(PiiDetectionConfig.class));
        ManagePiiDetectionConfigPort port = new ManagePiiDetectionConfigUseCase(repository, mock(JudgeCategoryPolicy.class));
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
                true, false, false, 2048, 410, new BigDecimal("0.30"), false,
                "localhost", 1234, null, null, 1, false, null, "admin", false, false, true, false, null, null, null, null);

        PiiDetectionConfig saved = port.updateConfig(command);

        assertThat(saved.trioEnabled()).isFalse();
        assertThat(saved.bardsEnabled()).isFalse();
        assertThat(saved.cbxEnabled()).isTrue();
        assertThat(saved.ettinEnabled()).isFalse();
    }
}
