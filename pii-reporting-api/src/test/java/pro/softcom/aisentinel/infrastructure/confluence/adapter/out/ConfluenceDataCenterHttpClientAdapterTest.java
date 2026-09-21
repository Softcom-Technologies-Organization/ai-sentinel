package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluenceDeploymentType;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that ConfluenceDataCenterHttpClientAdapter uses Bearer token (PAT).
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceDataCenterHttpClientAdapterTest {

    @Mock
    private ConfluenceConnectionConfig config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ConfluenceDataCenterHttpClientAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(config.baseUrl()).thenReturn("https://confluence.mycompany.com");
        lenient().when(config.username()).thenReturn("admin");
        lenient().when(config.apiToken()).thenReturn("dc-personal-access-token");
        lenient().when(config.deploymentType()).thenReturn(ConfluenceDeploymentType.DATA_CENTER);
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(0);
        lenient().when(config.pagesLimit()).thenReturn(50);

        adapter = new ConfluenceDataCenterHttpClientAdapter(config, new ObjectMapper());

        // Inject mocked HttpClient via reflection
        Field retryExecutorField = AbstractConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(adapter);

        Field httpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(retryExecutor, httpClient);
    }

    @Test
    void Should_UseBearerAuth_When_MakingHttpRequest() {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));

        // Act
        adapter.testConnection().join();

        // Assert - capture the request and verify authorization header
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        var authHeader = capturedRequest.headers().firstValue("Authorization").orElse("");

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(authHeader).isEqualTo("Bearer dc-personal-access-token");
        softly.assertThat(authHeader).startsWith("Bearer ");
        softly.assertThat(authHeader).doesNotContain("Basic");
        softly.assertAll();
    }

    @Test
    void Should_ReturnTrue_When_ConnectionSucceeds() {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));

        boolean result = adapter.testConnection().join();

        assertThat(result).isTrue();
    }

    @Test
    void Should_ReturnFalse_When_ConnectionFails() {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));

        boolean result = adapter.testConnection().join();

        assertThat(result).isFalse();
    }

    @Test
    void Should_DelegateToGetSpace_When_GetSpaceWithPermissionsCalled() {
        // Data Center does not support expand=permissions (CONFSERVER-78176)
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\":\"1\",\"key\":\"DC\",\"name\":\"DC Space\",\"type\":\"global\",\"status\":\"current\"}");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));

        var result = adapter.getSpaceWithPermissions("DC").join();

        // Assert - the request should NOT contain expand=permissions
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(capturedRequest.uri().toString()).doesNotContain("permissions");
        softly.assertThat(result).isPresent();
        softly.assertAll();
    }

    @Test
    void Should_TrimToken_When_TokenHasWhitespace() throws Exception {
        // Arrange - apiToken with leading/trailing spaces
        when(config.apiToken()).thenReturn("  dc-personal-access-token  ");
        adapter = new ConfluenceDataCenterHttpClientAdapter(config, new ObjectMapper());

        // Re-inject mocked HttpClient
        Field retryExecutorField = AbstractConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(adapter);
        Field httpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(retryExecutor, httpClient);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));

        // Act
        adapter.testConnection().join();

        // Assert
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        var authHeader = requestCaptor.getValue().headers().firstValue("Authorization").orElse("");
        assertThat(authHeader).isEqualTo("Bearer dc-personal-access-token");
    }
}
