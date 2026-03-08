package pro.softcom.aisentinel.infrastructure.jira.adapter.in;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import pro.softcom.aisentinel.application.jira.port.in.ManageJiraConnectionPort;
import pro.softcom.aisentinel.domain.jira.JiraConnectionSettings;
import pro.softcom.aisentinel.domain.jira.JiraDeploymentType;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.JiraConnectionConfigResponseDto;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.TestJiraConnectionRequestDto;
import pro.softcom.aisentinel.infrastructure.jira.adapter.in.dto.UpdateJiraConnectionConfigRequestDto;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JiraConnectionConfigController")
class JiraConnectionConfigControllerTest {

    @Mock
    private ManageJiraConnectionPort manageJiraConnectionPort;

    private JiraConnectionConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new JiraConnectionConfigController(manageJiraConnectionPort);
    }

    // ========== GET config ==========

    @Test
    @DisplayName("Should_ReturnConfig_When_GetConfigSucceeds")
    void Should_ReturnConfig_When_GetConfigSucceeds() {
        // Arrange
        JiraConnectionSettings settings = createSettings();
        when(manageJiraConnectionPort.getConnectionSettings()).thenReturn(settings);
        when(manageJiraConnectionPort.isConfigured()).thenReturn(true);

        // Act
        ResponseEntity<JiraConnectionConfigResponseDto> response = controller.getConfig();

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(200);
            JiraConnectionConfigResponseDto body = response.getBody();
            softly.assertThat(body).isNotNull();
            Assertions.assertNotNull(body);
            softly.assertThat(body.baseUrl()).isEqualTo("https://test.atlassian.net");
            softly.assertThat(body.email()).isEqualTo("user@test.com");
            softly.assertThat(body.apiToken()).isEqualTo("***");
            softly.assertThat(body.issuesLimit()).isEqualTo(50);
            softly.assertThat(body.maxIssues()).isEqualTo(5000);
            softly.assertThat(body.configured()).isTrue();
        });
    }

    @Test
    @DisplayName("Should_Return500_When_GetConfigThrows")
    void Should_Return500_When_GetConfigThrows() {
        // Arrange
        when(manageJiraConnectionPort.getConnectionSettings()).thenThrow(new RuntimeException("DB error"));

        // Act
        ResponseEntity<JiraConnectionConfigResponseDto> response = controller.getConfig();

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    // ========== PUT config ==========

    @Test
    @DisplayName("Should_ReturnUpdatedConfig_When_UpdateSucceeds")
    void Should_ReturnUpdatedConfig_When_UpdateSucceeds() {
        // Arrange
        JiraConnectionSettings updatedSettings = createSettings();
        when(manageJiraConnectionPort.updateConnectionSettings(any())).thenReturn(updatedSettings);
        when(manageJiraConnectionPort.isConfigured()).thenReturn(true);

        UpdateJiraConnectionConfigRequestDto request = new UpdateJiraConnectionConfigRequestDto(
                "https://test.atlassian.net", "user@test.com", "token",
                30000, 60000, 3, 50, 5000, JiraDeploymentType.CLOUD
        );

        // Act
        ResponseEntity<JiraConnectionConfigResponseDto> response = controller.updateConfig(request, null);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(200);
            softly.assertThat(response.getBody()).isNotNull();
            Assertions.assertNotNull(response.getBody());
            softly.assertThat(response.getBody().baseUrl()).isEqualTo("https://test.atlassian.net");
        });
    }

    @Test
    @DisplayName("Should_Return400_When_UpdateWithInvalidData")
    void Should_Return400_When_UpdateWithInvalidData() {
        // Arrange
        when(manageJiraConnectionPort.updateConnectionSettings(any()))
                .thenThrow(new IllegalArgumentException("Invalid baseUrl"));

        UpdateJiraConnectionConfigRequestDto request = new UpdateJiraConnectionConfigRequestDto(
                "invalid", "user@test.com", "token",
                30000, 60000, 3, 50, 5000, JiraDeploymentType.CLOUD
        );

        // Act
        ResponseEntity<JiraConnectionConfigResponseDto> response = controller.updateConfig(request, null);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    // ========== POST test ==========

    @Test
    @DisplayName("Should_ReturnSuccess_When_TestConnectionSucceeds")
    void Should_ReturnSuccess_When_TestConnectionSucceeds() {
        // Arrange
        when(manageJiraConnectionPort.testConnection(any())).thenReturn(true);

        TestJiraConnectionRequestDto request = new TestJiraConnectionRequestDto(
                "https://test.atlassian.net", "user@test.com", "token", JiraDeploymentType.CLOUD
        );

        // Act
        ResponseEntity<JiraConnectionConfigController.ConnectionTestResultDto> response =
                controller.testConnection(request);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(200);
            softly.assertThat(response.getBody()).isNotNull();
            Assertions.assertNotNull(response.getBody());
            softly.assertThat(response.getBody().success()).isTrue();
            softly.assertThat(response.getBody().message()).contains("successfully");
        });
    }

    @Test
    @DisplayName("Should_ReturnFailure_When_TestConnectionFails")
    void Should_ReturnFailure_When_TestConnectionFails() {
        // Arrange
        when(manageJiraConnectionPort.testConnection(any())).thenReturn(false);

        TestJiraConnectionRequestDto request = new TestJiraConnectionRequestDto(
                "https://test.atlassian.net", "user@test.com", "token", JiraDeploymentType.CLOUD
        );

        // Act
        ResponseEntity<JiraConnectionConfigController.ConnectionTestResultDto> response =
                controller.testConnection(request);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(200);
            softly.assertThat(response.getBody()).isNotNull();
            Assertions.assertNotNull(response.getBody());
            softly.assertThat(response.getBody().success()).isFalse();
            softly.assertThat(response.getBody().message()).contains("Failed");
        });
    }

    @Test
    @DisplayName("Should_Return500_When_TestConnectionThrows")
    void Should_Return500_When_TestConnectionThrows() {
        // Arrange
        when(manageJiraConnectionPort.testConnection(any())).thenThrow(new RuntimeException("Connection refused"));

        TestJiraConnectionRequestDto request = new TestJiraConnectionRequestDto(
                "https://test.atlassian.net", "user@test.com", "token", JiraDeploymentType.CLOUD
        );

        // Act
        ResponseEntity<JiraConnectionConfigController.ConnectionTestResultDto> response =
                controller.testConnection(request);

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(500);
            softly.assertThat(response.getBody()).isNotNull();
            softly.assertThat(response.getBody().success()).isFalse();
            softly.assertThat(response.getBody().message()).contains("Connection refused");
        });
    }

    // ========== Helpers ==========

    private JiraConnectionSettings createSettings() {
        return new JiraConnectionSettings(
                1,
                "https://test.atlassian.net",
                "user@test.com",
                30000,
                60000,
                3,
                50,
                5000,
                JiraDeploymentType.CLOUD,
                Instant.parse("2024-01-15T10:00:00Z"),
                "admin"
        );
    }
}
