package pro.softcom.aisentinel.application.confluence.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort.TestConfluenceConnectionCommand;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort.UpdateConfluenceConnectionCommand;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManageConfluenceConnectionUseCase")
class ManageConfluenceConnectionUseCaseTest {

    @Mock
    private ConfluenceConnectionConfigRepository repository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private Runnable onConfigUpdated;

    private ManageConfluenceConnectionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageConfluenceConnectionUseCase(repository, encryptionService, onConfigUpdated);
    }

    @Nested
    @DisplayName("getConnectionSettings")
    class GetConnectionSettings {

        @Test
        @DisplayName("Should return settings from repository")
        void Should_ReturnSettings_When_RepositoryHasConfig() {
            // Given
            var expected = createSettings("https://example.atlassian.net/wiki");
            when(repository.findConfig()).thenReturn(expected);

            // When
            var result = useCase.getConnectionSettings();

            // Then
            assertThat(result).isEqualTo(expected);
            verify(repository).findConfig();
        }
    }

    @Nested
    @DisplayName("isConfigured")
    class IsConfigured {

        @Test
        @DisplayName("Should return true when encrypted token exists")
        void Should_ReturnTrue_When_EncryptedTokenExists() {
            when(repository.getEncryptedApiToken()).thenReturn("ENC:v1:encrypted");

            assertThat(useCase.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should return false when encrypted token is null")
        void Should_ReturnFalse_When_EncryptedTokenIsNull() {
            when(repository.getEncryptedApiToken()).thenReturn(null);

            assertThat(useCase.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("Should return false when encrypted token is blank")
        void Should_ReturnFalse_When_EncryptedTokenIsBlank() {
            when(repository.getEncryptedApiToken()).thenReturn("   ");

            assertThat(useCase.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateConnectionSettings")
    class UpdateConnectionSettings {

        @Test
        @DisplayName("Should update settings when URL is valid HTTPS")
        void Should_UpdateSettings_When_UrlIsValidHttps() {
            // Given
            var command = createUpdateCommand("https://mycompany.atlassian.net/wiki", "token123");
            when(encryptionService.encrypt(eq("token123"), any())).thenReturn("ENC:v1:encrypted");

            // When
            var result = useCase.updateConnectionSettings(command);

            // Then
            assertThat(result.baseUrl()).isEqualTo("https://mycompany.atlassian.net/wiki");
            verify(repository).updateConfig(any(), eq("ENC:v1:encrypted"));
            verify(onConfigUpdated).run();
        }

        @Test
        @DisplayName("Should normalize trailing slash in URL")
        void Should_NormalizeTrailingSlash_When_UrlHasTrailingSlash() {
            // Given
            var command = createUpdateCommand("https://mycompany.atlassian.net/wiki/", "token123");
            when(encryptionService.encrypt(eq("token123"), any())).thenReturn("ENC:v1:encrypted");

            // When
            var result = useCase.updateConnectionSettings(command);

            // Then
            assertThat(result.baseUrl()).isEqualTo("https://mycompany.atlassian.net/wiki");
        }

        @ParameterizedTest(name = "Should reject unsafe URL: {0}")
        @CsvSource({
                "http://mycompany.atlassian.net/wiki, HTTPS",
                "https://127.0.0.1/wiki, localhost"
        })
        void Should_RejectUrl_When_UrlIsUnsafe(String url, String expectedMessage) {
            var command = createUpdateCommand(url, "token");

            assertThatThrownBy(() -> useCase.updateConnectionSettings(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(expectedMessage);

            verify(repository, never()).updateConfig(any(), any());
        }

        @Test
        @DisplayName("Should keep existing token when no new token provided")
        void Should_KeepExistingToken_When_NoNewTokenProvided() {
            // Given
            var command = createUpdateCommand("https://mycompany.atlassian.net/wiki", "");
            when(repository.getEncryptedApiToken()).thenReturn("ENC:v1:existing");

            // When
            useCase.updateConnectionSettings(command);

            // Then
            verify(repository).updateConfig(any(), eq("ENC:v1:existing"));
            verify(encryptionService, never()).encrypt(any(), any());
        }
    }

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("Should accept URL when host is private address")
        void Should_AcceptUrl_When_HostIsPrivateAddress() {
            var command = new TestConfluenceConnectionCommand("https://10.0.0.1/wiki", "user", "token", ConfluenceDeploymentType.CLOUD);

            // Private network addresses are valid for corporate Confluence
            // URL validation passes, connection fails gracefully returning false
            boolean result = useCase.testConnection(command);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should reject URL when scheme is HTTP (SSRF protection)")
        void Should_RejectUrl_When_SchemeIsHttp() {
            var command = new TestConfluenceConnectionCommand("http://public.example.com/wiki", "user", "token", ConfluenceDeploymentType.CLOUD);

            assertThatThrownBy(() -> useCase.testConnection(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("Should reject URL when host is localhost (SSRF protection)")
        void Should_RejectUrl_When_HostIsLocalhost() {
            var command = new TestConfluenceConnectionCommand("https://localhost/wiki", "user", "token", ConfluenceDeploymentType.CLOUD);

            assertThatThrownBy(() -> useCase.testConnection(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("localhost");
        }
    }

    @Nested
    @DisplayName("buildAuthHeader — dual auth verification")
    class BuildAuthHeader {

        @Test
        @DisplayName("Should build Basic Auth header for Cloud deployment")
        void Should_BuildBasicAuth_When_DeploymentTypeIsCloud() {
            var command = new TestConfluenceConnectionCommand(
                    "https://mycompany.atlassian.net/wiki", "user@example.com", "cloud-api-token",
                    ConfluenceDeploymentType.CLOUD);

            String header = useCase.buildAuthHeader(command);

            String expectedCredentials = java.util.Base64.getEncoder()
                    .encodeToString("user@example.com:cloud-api-token".getBytes());
            assertThat(header).isEqualTo("Basic " + expectedCredentials);
        }

        @Test
        @DisplayName("Should build Bearer Auth header for Data Center deployment")
        void Should_BuildBearerAuth_When_DeploymentTypeIsDataCenter() {
            var command = new TestConfluenceConnectionCommand(
                    "https://confluence.mycompany.com", "admin", "dc-personal-access-token",
                    ConfluenceDeploymentType.DATA_CENTER);

            String header = useCase.buildAuthHeader(command);

            assertThat(header).isEqualTo("Bearer dc-personal-access-token");
        }

        @Test
        @DisplayName("Should default to Basic Auth when deployment type is null")
        void Should_DefaultToBasicAuth_When_DeploymentTypeIsNull() {
            var command = new TestConfluenceConnectionCommand(
                    "https://mycompany.atlassian.net/wiki", "user@example.com", "token", null);

            String header = useCase.buildAuthHeader(command);

            assertThat(header).startsWith("Basic ");
        }

        @Test
        @DisplayName("Should not include username in Bearer header for Data Center")
        void Should_NotIncludeUsername_When_DeploymentTypeIsDataCenter() {
            var command = new TestConfluenceConnectionCommand(
                    "https://confluence.mycompany.com", "admin", "my-pat-token",
                    ConfluenceDeploymentType.DATA_CENTER);

            String header = useCase.buildAuthHeader(command);

            assertThat(header)
                    .doesNotContain("admin")
                    .doesNotContain("Basic")
                    .isEqualTo("Bearer my-pat-token");
        }

        @Test
        @DisplayName("Should include email in Basic header for Cloud")
        void Should_IncludeEmail_When_DeploymentTypeIsCloud() {
            var command = new TestConfluenceConnectionCommand(
                    "https://mycompany.atlassian.net/wiki", "user@corp.com", "api-token",
                    ConfluenceDeploymentType.CLOUD);

            String header = useCase.buildAuthHeader(command);
            String decoded = new String(java.util.Base64.getDecoder()
                    .decode(header.substring("Basic ".length())));

            assertThat(decoded).isEqualTo("user@corp.com:api-token");
        }
    }

    private UpdateConfluenceConnectionCommand createUpdateCommand(String baseUrl, String apiToken) {
        return new UpdateConfluenceConnectionCommand(
                baseUrl, "user@example.com", apiToken,
                5000, 30000, 3, 25,
                ConfluenceDeploymentType.CLOUD, "test-user"
        );
    }

    private ConfluenceConnectionSettings createSettings(String baseUrl) {
        return new ConfluenceConnectionSettings(
                1, baseUrl, "user@example.com",
                5000, 30000, 3, 25,
                ConfluenceDeploymentType.CLOUD,
                Instant.now(), "test-user"
        );
    }
}
