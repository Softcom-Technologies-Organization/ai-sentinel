package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultConfluenceConnectionConfig - Tests de validation et fonctionnalités")
class ConfluenceConfigTest {

    @Test
    @DisplayName("Should_CreateValidConfig_When_AllParametersValid")
    void shouldCreateValidConfigWhenAllParametersValid() {
        // Arrange & Act
        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/",
                "/content/search",
                "/space",
                "/child/attachment",
                "body.storage,version,metadata,ancestors",
                "permissions,metadata"
            )
        );

        // Assert
        assertThat(config).isNotNull();
        assertThat(config.isValid()).isTrue();
        assertThat(config.baseUrl()).isEqualTo("https://confluence.example.com");
        assertThat(config.username()).isEqualTo("testuser");
        assertThat(config.apiToken()).isEqualTo("testtoken");
        assertThat(config.spaceKey()).isEqualTo("TESTSPACE");
    }

    @Test
    @DisplayName("Should_ThrowException_When_BaseUrlIsNull")
    void shouldThrowExceptionWhenBaseUrlIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> new ConfluenceConfig(
            null,
            "testuser",
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl must not be null or blank");
    }

    @Test
    @DisplayName("Should_ThrowException_When_BaseUrlIsBlank")
    void shouldThrowExceptionWhenBaseUrlIsBlank() {
        // Act & Assert
        assertThatThrownBy(() -> new ConfluenceConfig(
            "  ",
            "testuser",
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl must not be null or blank");
    }

    @Test
    @DisplayName("Should_ThrowException_When_UsernameIsNull")
    void shouldThrowExceptionWhenUsernameIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> new ConfluenceConfig(
            "https://confluence.example.com",
            null,
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("username must not be null or blank");
    }

    @Test
    @DisplayName("Should_ThrowException_When_ApiTokenIsNull")
    void shouldThrowExceptionWhenApiTokenIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            null,
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("apiToken must not be null or blank");
    }

    @Test
    @DisplayName("Should_ReturnFalse_When_SpaceKeyIsNull")
    void shouldReturnFalseWhenSpaceKeyIsNull() {
        // Arrange
        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            "testtoken",
            null,
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        );

        // Act & Assert
        assertThat(config.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should_ApplyDefaults_When_ConnectionSettingsInvalid")
    void shouldApplyDefaultsWhenConnectionSettingsInvalid() {
        // Arrange & Act
        var connectionSettings = new ConfluenceConfig.ConnectionSettings(
            -1,    // Invalid connectTimeout
            -1,    // Invalid readTimeout
            -1,    // Invalid maxRetries
            false,
            null
        );

        // Assert - defaults should be applied
        assertThat(connectionSettings.connectTimeout()).isEqualTo(30000);
        assertThat(connectionSettings.readTimeout()).isEqualTo(60000);
        assertThat(connectionSettings.maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should_ApplyDefaults_When_PaginationSettingsInvalid")
    void shouldApplyDefaultsWhenPaginationSettingsInvalid() {
        // Arrange & Act
        var paginationSettings = new ConfluenceConfig.PaginationSettings(
            -1,  // Invalid pagesLimit
            -1   // Invalid maxPages
        );

        // Assert - defaults should be applied
        assertThat(paginationSettings.pagesLimit()).isEqualTo(50);
        assertThat(paginationSettings.maxPages()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should_ReturnCorrectRestApiUrl_When_BaseUrlEndsWithSlash")
    void shouldReturnCorrectRestApiUrlWhenBaseUrlEndsWithSlash() {
        // Arrange
        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com/",
            "testuser",
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        );

        // Act & Assert
        assertThat(config.getRestApiUrl()).isEqualTo("https://confluence.example.com/rest/api");
    }

    @Test
    @DisplayName("Should_ReturnCorrectRestApiUrl_When_BaseUrlDoesNotEndWithSlash")
    void shouldReturnCorrectRestApiUrlWhenBaseUrlDoesNotEndWithSlash() {
        // Arrange
        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            "testtoken",
            "TESTSPACE",
            new ConfluenceConfig.ConnectionSettings(30000, 60000, 3, false, null),
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        );

        // Act & Assert
        assertThat(config.getRestApiUrl()).isEqualTo("https://confluence.example.com/rest/api");
    }

    @Test
    @DisplayName("Should_DelegateCorrectly_When_AccessingScalarProperties")
    void shouldDelegateCorrectlyWhenAccessingScalarProperties() {
        // Arrange
        var proxySettings = new ConfluenceConfig.ProxySettings(
            "proxy.example.com",
            8080,
            "proxyuser",
            "proxypass"
        );

        var connectionSettings = new ConfluenceConfig.ConnectionSettings(
            15000,
            30000,
            5,
            true,
            proxySettings
        );

        var paginationSettings = new ConfluenceConfig.PaginationSettings(25, 50);

        var apiPaths = new ConfluenceConfig.ApiPaths(
            "/api/content/",
            "/api/content/search",
            "/api/space",
            "/api/child/attachment",
            "body.storage,version",
            "permissions"
        );

        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            "testtoken",
            "TESTSPACE",
            connectionSettings,
            paginationSettings,
            apiPaths
        );

        // Act & Assert
        assertThat(config.connectTimeout()).isEqualTo(15000);
        assertThat(config.readTimeout()).isEqualTo(30000);
        assertThat(config.maxRetries()).isEqualTo(5);
        assertThat(config.enableProxy()).isTrue();
        assertThat(config.proxyHost()).isEqualTo("proxy.example.com");
        assertThat(config.proxyPort()).isEqualTo(8080);
        assertThat(config.proxyUsername()).isEqualTo("proxyuser");
        assertThat(config.proxyPassword()).isEqualTo("proxypass");
        assertThat(config.pagesLimit()).isEqualTo(25);
        assertThat(config.maxPages()).isEqualTo(50);
        assertThat(config.contentPath()).isEqualTo("/api/content/");
        assertThat(config.searchContentPath()).isEqualTo("/api/content/search");
        assertThat(config.spacePath()).isEqualTo("/api/space");
        assertThat(config.attachmentChildSuffix()).isEqualTo("/api/child/attachment");
        assertThat(config.defaultPageExpands()).isEqualTo("body.storage,version");
        assertThat(config.defaultSpaceExpands()).isEqualTo("permissions");
    }

    @Test
    @DisplayName("Should_ReturnNull_When_ProxySettingsIsNull")
    void shouldReturnNullWhenProxySettingsIsNull() {
        // Arrange
        var connectionSettings = new ConfluenceConfig.ConnectionSettings(
            30000,
            60000,
            3,
            false,
            null  // No proxy settings
        );

        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.example.com",
            "testuser",
            "testtoken",
            "TESTSPACE",
            connectionSettings,
            new ConfluenceConfig.PaginationSettings(50, 100),
            new ConfluenceConfig.ApiPaths(
                "/content/", "/content/search", "/space", "/child/attachment",
                "body.storage,version", "permissions"
            )
        );

        // Act & Assert
        assertThat(config.proxyHost()).isNull();
        assertThat(config.proxyPort()).isZero();
        assertThat(config.proxyUsername()).isNull();
        assertThat(config.proxyPassword()).isNull();
    }
}
