package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort.UpdatePiiDetectionConfigCommand;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ManagePiiDetectionConfigUseCase.
 */
@ExtendWith(MockitoExtension.class)
class ManagePiiDetectionConfigUseCaseTest {

    @Mock
    private PiiDetectionConfigRepository repository;

    @InjectMocks
    private ManagePiiDetectionConfigUseCase useCase;

    @Test
    void Should_ReturnConfig_When_GetConfigCalled() {
        // Arrange
        PiiDetectionConfig expectedConfig = new PiiDetectionConfig(
            1, true, true, false, new BigDecimal("0.75"),30, LocalDateTime.now(), "system"
        );
        when(repository.findConfig()).thenReturn(expectedConfig);

        // Act
        PiiDetectionConfig result = useCase.getConfig();

        // Assert
        assertThat(result).isEqualTo(expectedConfig);
        verify(repository).findConfig();
    }

    @Test
    void Should_UpdateAndReturnConfig_When_ValidCommand() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, true, new BigDecimal("0.80"),30, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());

        PiiDetectionConfig savedConfig = captor.getValue();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(savedConfig.getId()).isEqualTo(1);
        softly.assertThat(savedConfig.isGlinerEnabled()).isTrue();
        softly.assertThat(savedConfig.isPresidioEnabled()).isFalse();
        softly.assertThat(savedConfig.isRegexEnabled()).isTrue();
        softly.assertThat(savedConfig.getDefaultThreshold()).isEqualByComparingTo(new BigDecimal("0.80"));
        softly.assertThat(savedConfig.getUpdatedBy()).isEqualTo("testuser");
        softly.assertThat(savedConfig.getUpdatedAt()).isNotNull();
        softly.assertAll();

        assertThat(result).isEqualTo(savedConfig);
    }

    @Test
    void Should_ThrowException_When_CommandHasInvalidThreshold() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, new BigDecimal("1.5"),30, "testuser"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Default threshold must be less than or equal to 1");
    }

    @Test
    void Should_ThrowException_When_CommandHasNegativeThreshold() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, new BigDecimal("-0.1"), 30,"testuser"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Default threshold must be greater than or equal to 0");
    }

    @Test
    void Should_ThrowException_When_NoDetectorsEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            false, false, false, new BigDecimal("0.75"), 30,"testuser"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one detector must be enabled");
    }

    @Test
    void Should_UpdateConfig_When_OnlyGlinerEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, new BigDecimal("0.75"), 30,"testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.isGlinerEnabled()).isTrue();
        softly.assertThat(result.isPresidioEnabled()).isFalse();
        softly.assertThat(result.isRegexEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_UpdateConfig_When_OnlyPresidioEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            false, true, false, new BigDecimal("0.75"), 30,"testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.isGlinerEnabled()).isFalse();
        softly.assertThat(result.isPresidioEnabled()).isTrue();
        softly.assertThat(result.isRegexEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_UpdateConfig_When_OnlyRegexEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            false, false, true, new BigDecimal("0.75"), 30,"testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.isGlinerEnabled()).isFalse();
        softly.assertThat(result.isPresidioEnabled()).isFalse();
        softly.assertThat(result.isRegexEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void Should_AcceptBoundaryThreshold_When_ThresholdIsZero() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, BigDecimal.ZERO, 30,"testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        assertThat(result.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void Should_AcceptBoundaryThreshold_When_ThresholdIsOne() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, BigDecimal.ONE, 30,"testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        assertThat(result.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void Should_SetConfigIdToOne_When_UpdatingConfig() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, new BigDecimal("0.75"), 30,"testuser"
        );

        // Act
        useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1);
    }

    @Test
    void Should_PropagateException_When_RepositoryThrowsException() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, new BigDecimal("0.75"), 30,"testuser"
        );
        doThrow(new RuntimeException("Database error"))
            .when(repository).updateConfig(any());

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database error");
    }
}
