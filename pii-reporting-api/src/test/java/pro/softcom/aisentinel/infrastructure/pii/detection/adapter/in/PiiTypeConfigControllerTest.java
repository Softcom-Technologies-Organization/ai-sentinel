package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.CategoryGroupResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.GroupedPiiTypesResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto.UpdatePiiTypeConfigRequestDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PiiTypeConfigController")
class PiiTypeConfigControllerTest {

    @Mock
    private ManagePiiTypeConfigsPort managePiiTypeConfigsPort;

    @InjectMocks
    private PiiTypeConfigController controller;

    @Test
    @DisplayName("Should_IncludeRegexDetector_When_GetGroupedForUI")
    void Should_IncludeRegexDetector_When_GetGroupedForUI() {
        // Arrange
        var ministralConfig = PiiTypeConfig.builder()
                .piiType("EMAIL")
                .detector("MINISTRAL")
                .enabled(true)
                .threshold(0.80)
                .category("CONTACT")
                .detectorLabel("email address")
                .severity("LOW")
                .build();
        var presidioConfig = PiiTypeConfig.builder()
                .piiType("CREDIT_CARD")
                .detector("PRESIDIO")
                .enabled(true)
                .threshold(0.75)
                .category("Financial")
                .detectorLabel("CREDIT_CARD")
                .severity("HIGH")
                .build();
        var regexConfig = PiiTypeConfig.builder()
                .piiType("SOCIALNUM")
                .detector("REGEX")
                .enabled(true)
                .threshold(0.75)
                .category("IDENTITY")
                .detectorLabel("social security number")
                .severity("HIGH")
                .build();

        when(managePiiTypeConfigsPort.getAllConfigs())
                .thenReturn(List.of(ministralConfig, presidioConfig, regexConfig));

        // Act
        var response = controller.getGroupedForUI();

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            List<GroupedPiiTypesResponseDto> body = response.getBody();
            softly.assertThat(body).isNotNull();
            softly.assertThat(body).hasSize(3);

            Assertions.assertNotNull(body);
            List<String> detectors = body.stream()
                    .map(GroupedPiiTypesResponseDto::detector)
                    .toList();
            softly.assertThat(detectors).containsExactly("MINISTRAL", "PRESIDIO", "REGEX");
        });
    }

    @Test
    @DisplayName("Should_GroupByDetectorThenCategory_When_GetGroupedForUI")
    void Should_GroupByDetectorThenCategory_When_GetGroupedForUI() {
        // Arrange
        var regexIdentity = PiiTypeConfig.builder()
                .piiType("SOCIALNUM")
                .detector("REGEX")
                .enabled(true)
                .threshold(0.75)
                .category("IDENTITY")
                .detectorLabel("social security number")
                .severity("HIGH")
                .build();
        var regexFinancial = PiiTypeConfig.builder()
                .piiType("CREDIT_CARD_NUMBER")
                .detector("REGEX")
                .enabled(true)
                .threshold(0.90)
                .category("FINANCIAL")
                .detectorLabel("credit card number")
                .severity("HIGH")
                .build();

        when(managePiiTypeConfigsPort.getAllConfigs())
                .thenReturn(List.of(regexIdentity, regexFinancial));

        // Act
        var response = controller.getGroupedForUI();

        // Assert
        var body = response.getBody();
        assertThat(body).hasSize(1);

        var regexGroup = body.getFirst();
        assertSoftly(softly -> {
            softly.assertThat(regexGroup.detector()).isEqualTo("REGEX");
            softly.assertThat(regexGroup.categories()).hasSize(2);

            List<String> categories = regexGroup.categories().stream()
                    .map(CategoryGroupResponseDto::category)
                    .toList();
            softly.assertThat(categories).containsExactly("FINANCIAL", "IDENTITY");
        });
    }

    @Test
    @DisplayName("Should_ReturnEmptyList_When_NoConfigsExist")
    void Should_ReturnEmptyList_When_NoConfigsExist() {
        // Arrange
        when(managePiiTypeConfigsPort.getAllConfigs()).thenReturn(Collections.emptyList());

        // Act
        var response = controller.getGroupedForUI();

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            softly.assertThat(response.getBody()).isEmpty();
        });
    }

    @Test
    @DisplayName("Should_SortDetectorsAlphabetically_When_GetGroupedForUI")
    void Should_SortDetectorsAlphabetically_When_GetGroupedForUI() {
        // Arrange - insert in reverse order to verify sorting
        var regexConfig = PiiTypeConfig.builder()
                .piiType("SOCIALNUM").detector("REGEX").enabled(true)
                .threshold(0.75).category("IDENTITY").detectorLabel("social security number")
                .severity("HIGH").build();
        var ministralConfig = PiiTypeConfig.builder()
                .piiType("EMAIL").detector("MINISTRAL").enabled(true)
                .threshold(0.80).category("CONTACT").detectorLabel("email address")
                .severity("LOW").build();
        var presidioConfig = PiiTypeConfig.builder()
                .piiType("CREDIT_CARD").detector("PRESIDIO").enabled(true)
                .threshold(0.75).category("Financial").detectorLabel("CREDIT_CARD")
                .severity("HIGH").build();

        when(managePiiTypeConfigsPort.getAllConfigs())
                .thenReturn(List.of(regexConfig, ministralConfig, presidioConfig));

        // Act
        var response = controller.getGroupedForUI();

        // Assert
        Assertions.assertNotNull(response.getBody());
        List<String> detectors = response.getBody().stream()
                .map(GroupedPiiTypesResponseDto::detector)
                .toList();
        assertThat(detectors).containsExactly("MINISTRAL", "PRESIDIO", "REGEX");
    }

    @Test
    @DisplayName("Should_ReturnAllConfigs_When_GetAllConfigsCalled")
    void Should_ReturnAllConfigs_When_GetAllConfigsCalled() {
        // Arrange
        var config = PiiTypeConfig.builder()
                .piiType("EMAIL").detector("MINISTRAL").enabled(true)
                .threshold(0.80).category("CONTACT").detectorLabel("email").severity("LOW").build();
        when(managePiiTypeConfigsPort.getAllConfigs()).thenReturn(List.of(config));

        // Act
        var response = controller.getAllConfigs();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().piiType()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("Should_ReturnConfigsByDetector_When_GetConfigsByDetectorCalled")
    void Should_ReturnConfigsByDetector_When_GetConfigsByDetectorCalled() {
        // Arrange
        var config = PiiTypeConfig.builder()
                .piiType("CREDIT_CARD").detector("PRESIDIO").enabled(true)
                .threshold(0.75).category("Financial").detectorLabel("credit card").severity("HIGH").build();
        when(managePiiTypeConfigsPort.getConfigsByDetector("PRESIDIO")).thenReturn(List.of(config));

        // Act
        var response = controller.getConfigsByDetector("PRESIDIO");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().detector()).isEqualTo("PRESIDIO");
    }

    @Test
    @DisplayName("Should_ReturnConfigsByCategory_When_GetConfigsByCategoryCalled")
    void Should_ReturnConfigsByCategory_When_GetConfigsByCategoryCalled() {
        // Arrange
        var config = PiiTypeConfig.builder()
                .piiType("EMAIL").detector("MINISTRAL").enabled(true)
                .threshold(0.80).category("CONTACT").detectorLabel("email").severity("LOW").build();
        when(managePiiTypeConfigsPort.getConfigsByCategory())
                .thenReturn(Map.of("CONTACT", List.of(config)));

        // Act
        var response = controller.getConfigsByCategory();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("CONTACT");
        assertThat(response.getBody().get("CONTACT")).hasSize(1);
    }

    @Test
    @DisplayName("Should_ReturnUpdated_When_UpdateConfigCalled")
    void Should_ReturnUpdated_When_UpdateConfigCalled() {
        // Arrange
        var updated = PiiTypeConfig.builder()
                .piiType("EMAIL").detector("MINISTRAL").enabled(false)
                .threshold(0.90).category("CONTACT").detectorLabel("email").severity("LOW").build();
        when(managePiiTypeConfigsPort.updateConfig(anyString(), anyString(), anyBoolean(),
                anyDouble(), anyString())).thenReturn(updated);
        var request = new UpdatePiiTypeConfigRequestDto("EMAIL", "MINISTRAL", false, 0.90);

        // Act
        var response = controller.updateConfig("MINISTRAL", "EMAIL", request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().enabled()).isFalse();
    }

    @Test
    @DisplayName("Should_ThrowIllegalArgument_When_UpdateConfigPathMismatch")
    void Should_ThrowIllegalArgument_When_UpdateConfigPathMismatch() {
        var request = new UpdatePiiTypeConfigRequestDto("EMAIL", "PRESIDIO", true, 0.80);

        assertThatThrownBy(() -> controller.updateConfig("MINISTRAL", "EMAIL", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path parameters must match request body values");
    }

    @Test
    @DisplayName("Should_ReturnBulkUpdated_When_BulkUpdateCalled")
    void Should_ReturnBulkUpdated_When_BulkUpdateCalled() {
        // Arrange
        var updated = PiiTypeConfig.builder()
                .piiType("EMAIL").detector("MINISTRAL").enabled(true)
                .threshold(0.85).category("CONTACT").detectorLabel("email").severity("LOW").build();
        when(managePiiTypeConfigsPort.bulkUpdate(any(), anyString())).thenReturn(List.of(updated));
        var request = List.of(new UpdatePiiTypeConfigRequestDto("EMAIL", "MINISTRAL", true, 0.85));

        // Act
        var response = controller.bulkUpdate(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}
