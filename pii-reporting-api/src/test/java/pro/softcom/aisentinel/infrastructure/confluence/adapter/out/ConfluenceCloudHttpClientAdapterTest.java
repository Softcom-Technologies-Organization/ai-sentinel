package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that ConfluenceCloudHttpClientAdapter uses Basic Auth (email:apiToken).
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceCloudHttpClientAdapterTest {

    @Mock
    private ConfluenceConnectionConfig config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ConfluenceCloudHttpClientAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(config.baseUrl()).thenReturn("https://mycompany.atlassian.net/wiki");
        lenient().when(config.username()).thenReturn("user@example.com");
        lenient().when(config.apiToken()).thenReturn("cloud-api-token");
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(0);
        lenient().when(config.pagesLimit()).thenReturn(50);

        adapter = new ConfluenceCloudHttpClientAdapter(config, new ObjectMapper());

        // Inject mocked HttpClient via reflection
        Field retryExecutorField = AbstractConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(adapter);

        Field httpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(retryExecutor, httpClient);
    }

    @Test
    void Should_UseBasicAuth_When_MakingHttpRequest() {
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

        String expectedCredentials = "user@example.com:cloud-api-token";
        String expectedEncoded = Base64.getEncoder().encodeToString(
            expectedCredentials.getBytes(StandardCharsets.UTF_8));
        String expectedHeader = "Basic " + expectedEncoded;

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(authHeader).isEqualTo(expectedHeader);
        softly.assertThat(authHeader).startsWith("Basic ");
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
}
