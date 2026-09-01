package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagePiiTypeConfigsUseCase")
class ManagePiiTypeConfigsUseCaseTest {

    @Mock
    private PiiTypeConfigRepository repository;

    @InjectMocks
    private ManagePiiTypeConfigsUseCase useCase;

    // ====== updateConfig detector validation ======

    @Test
    @DisplayName("Should_AcceptMinistralDetector_When_Validating")
    void Should_AcceptMinistralDetector_When_Validating() {
        when(repository.updateAtomically("EMAIL", "MINISTRAL", true, 0.5, "admin"))
                .thenReturn(PiiTypeConfig.builder()
                        .piiType("EMAIL").detector("MINISTRAL").enabled(true).threshold(0.5).build());

        PiiTypeConfig result = useCase.updateConfig("EMAIL", "MINISTRAL", true, 0.5, "admin");

        assertThat(result.getDetector()).isEqualTo("MINISTRAL");
        verify(repository).updateAtomically("EMAIL", "MINISTRAL", true, 0.5, "admin");
    }

    @Test
    @DisplayName("Should_RejectUnknownDetector_When_Validating")
    void Should_RejectUnknownDetector_When_Validating() {
        assertThatThrownBy(() -> useCase.updateConfig("EMAIL", "UNKNOWN", true, 0.5, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MINISTRAL");
    }
}
