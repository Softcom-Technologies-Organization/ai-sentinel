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
import java.time.ZoneId;

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
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(ZoneId.systemDefault()), "system"
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
            false, true, false, 1024, 128, new BigDecimal("0.80"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());

        PiiDetectionConfig savedConfig = captor.getValue();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(savedConfig.id()).isOne();
        softly.assertThat(savedConfig.presidioEnabled()).isFalse();
        softly.assertThat(savedConfig.regexEnabled()).isTrue();
        softly.assertThat(savedConfig.defaultThreshold()).isEqualByComparingTo(new BigDecimal("0.80"));
        softly.assertThat(savedConfig.updatedBy()).isEqualTo("testuser");
        softly.assertThat(savedConfig.updatedAt()).isNotNull();
        softly.assertThat(savedConfig.postfilterEnabled()).isFalse();
        softly.assertAll();

        assertThat(result).isEqualTo(savedConfig);
    }

    @Test
    void Should_PassPostfilterEnabledFlagThroughUseCase_When_CommandEnablesIt() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, true, 1024, 128, new BigDecimal("0.80"), true, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captor.getValue().postfilterEnabled()).isTrue();
        softly.assertThat(result.postfilterEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void Should_PassMinistralFieldsThroughUseCase_When_CommandEnablesIt() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, true, 2048, 256, new BigDecimal("0.80"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captor.getValue().ministralEnabled()).isTrue();
        softly.assertThat(captor.getValue().ministralChunkSize()).isEqualTo(2048);
        softly.assertThat(captor.getValue().ministralOverlap()).isEqualTo(256);
        softly.assertThat(result.ministralEnabled()).isTrue();
        softly.assertThat(result.ministralChunkSize()).isEqualTo(2048);
        softly.assertThat(result.ministralOverlap()).isEqualTo(256);
        softly.assertAll();
    }

    @Test
    void Should_PassMinistralConcurrencyFieldsThroughUseCase_When_CommandSetsThem() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, true, 2048, 256, new BigDecimal("0.80"), false, "localhost", 1234, null,
            4, false, "localhost:1234|ministral", "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captor.getValue().ministralConcurrency()).isEqualTo(4);
        softly.assertThat(captor.getValue().ministralConcurrencyAuto()).isFalse();
        softly.assertThat(captor.getValue().ministralConcurrencyTunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertThat(result.ministralConcurrency()).isEqualTo(4);
        softly.assertThat(result.ministralConcurrencyAuto()).isFalse();
        softly.assertThat(result.ministralConcurrencyTunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertAll();
    }

    @Test
    void Should_ThrowException_When_CommandHasInvalidConcurrency() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null,
            21, false, null, "testuser"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ministral concurrency must be between 1 and 20");
    }

    @Test
    void Should_ThrowException_When_CommandHasInvalidThreshold() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, 1024, 128, new BigDecimal("1.5"), false, "localhost", 1234, null, 1, true, null, "testuser"
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
            true, true, false, 1024, 128, new BigDecimal("-0.1"), false, "localhost", 1234, null, 1, true, null, "testuser"
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
            false, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one detector must be enabled");
    }

    @Test
    void Should_UpdateConfig_When_OnlyPresidioEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.presidioEnabled()).isTrue();
        softly.assertThat(result.regexEnabled()).isFalse();
        softly.assertThat(result.ministralEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_UpdateConfig_When_OnlyRegexEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            false, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.presidioEnabled()).isFalse();
        softly.assertThat(result.regexEnabled()).isTrue();
        softly.assertThat(result.ministralEnabled()).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_UpdateConfig_When_OnlyMinistralEnabled() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            false, false, true, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.presidioEnabled()).isFalse();
        softly.assertThat(result.regexEnabled()).isFalse();
        softly.assertThat(result.ministralEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void Should_AcceptBoundaryThreshold_When_ThresholdIsZero() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, 1024, 128, BigDecimal.ZERO, false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        assertThat(result.defaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void Should_AcceptBoundaryThreshold_When_ThresholdIsOne() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, false, 1024, 128, BigDecimal.ONE, false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        PiiDetectionConfig result = useCase.updateConfig(command);

        // Assert
        assertThat(result.defaultThreshold()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void Should_SetConfigIdToOne_When_UpdatingConfig() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );

        // Act
        useCase.updateConfig(command);

        // Assert
        ArgumentCaptor<PiiDetectionConfig> captor = ArgumentCaptor.forClass(PiiDetectionConfig.class);
        verify(repository).updateConfig(captor.capture());
        assertThat(captor.getValue().id()).isOne();
    }

    @Test
    void Should_PropagateException_When_RepositoryThrowsException() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null, "testuser"
        );
        doThrow(new RuntimeException("Database error"))
            .when(repository).updateConfig(any());

        // Act & Assert
        assertThatThrownBy(() -> useCase.updateConfig(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database error");
    }
}
