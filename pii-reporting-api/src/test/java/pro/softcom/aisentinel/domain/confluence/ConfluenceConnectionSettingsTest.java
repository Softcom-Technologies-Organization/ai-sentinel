package pro.softcom.aisentinel.domain.confluence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("ConfluenceConnectionSettings")
class ConfluenceConnectionSettingsTest {

    private ConfluenceConnectionSettings validSettings() {
        return new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin",
                5000, 30000, 3, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        );
    }

    @Test
    @DisplayName("Should_CreateSettings_When_AllFieldsValid")
    void Should_CreateSettings_When_AllFieldsValid() {
        // Act
        ConfluenceConnectionSettings settings = validSettings();

        // Assert
        assertSoftly(softly -> {
            softly.assertThat(settings.baseUrl()).isEqualTo("https://example.atlassian.net");
            softly.assertThat(settings.username()).isEqualTo("admin");
            softly.assertThat(settings.connectTimeout()).isEqualTo(5000);
            softly.assertThat(settings.readTimeout()).isEqualTo(30000);
            softly.assertThat(settings.maxRetries()).isEqualTo(3);
            softly.assertThat(settings.pagesLimit()).isEqualTo(25);
            softly.assertThat(settings.deploymentType()).isEqualTo(ConfluenceDeploymentType.CLOUD);
        });
    }

    @Test
    @DisplayName("Should_DefaultBaseUrlToEmpty_When_BaseUrlIsNull")
    void Should_DefaultBaseUrlToEmpty_When_BaseUrlIsNull() {
        ConfluenceConnectionSettings settings = new ConfluenceConnectionSettings(
                1, null, "admin", 5000, 30000, 0, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        );
        assertThat(settings.baseUrl()).isEmpty();
    }

    @Test
    @DisplayName("Should_DefaultUsernameToEmpty_When_UsernameIsNull")
    void Should_DefaultUsernameToEmpty_When_UsernameIsNull() {
        ConfluenceConnectionSettings settings = new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", null, 5000, 30000, 0, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        );
        assertThat(settings.username()).isEmpty();
    }

    @Test
    @DisplayName("Should_DefaultDeploymentTypeToCloud_When_DeploymentTypeIsNull")
    void Should_DefaultDeploymentTypeToCloud_When_DeploymentTypeIsNull() {
        ConfluenceConnectionSettings settings = new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin", 5000, 30000, 0, 25,
                null, null, null
        );
        assertThat(settings.deploymentType()).isEqualTo(ConfluenceDeploymentType.CLOUD);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 30000",
        "-1, 30000"
    })
    @DisplayName("Should_ThrowException_When_ConnectTimeoutIsNotPositive")
    void Should_ThrowException_When_ConnectTimeoutIsNotPositive(int connectTimeout, int readTimeout) {
        assertThatThrownBy(() -> new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin",
                connectTimeout, readTimeout, 0, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connect timeout");
    }

    @Test
    @DisplayName("Should_ThrowException_When_ReadTimeoutIsZero")
    void Should_ThrowException_When_ReadTimeoutIsZero() {
        assertThatThrownBy(() -> new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin",
                5000, 0, 0, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Read timeout");
    }

    @Test
    @DisplayName("Should_ThrowException_When_MaxRetriesIsNegative")
    void Should_ThrowException_When_MaxRetriesIsNegative() {
        assertThatThrownBy(() -> new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin",
                5000, 30000, -1, 25,
                ConfluenceDeploymentType.CLOUD, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max retries");
    }

    @Test
    @DisplayName("Should_ThrowException_When_PagesLimitIsZero")
    void Should_ThrowException_When_PagesLimitIsZero() {
        assertThatThrownBy(() -> new ConfluenceConnectionSettings(
                1, "https://example.atlassian.net", "admin",
                5000, 30000, 0, 0,
                ConfluenceDeploymentType.CLOUD, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pages limit");
    }
}
