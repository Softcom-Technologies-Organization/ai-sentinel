package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de la politique de retry pour ConfluenceHttpClientAdapter.
 * 
 * Portée: logique de retry (n max), décisions de ré-essai selon statut HTTP,
 * comportement après épuisement des tentatives.
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceHttpClientAdapterRetryPolicyTest {

    @Mock
    private ConfluenceConnectionConfig config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ConfluenceHttpClientAdapter confluenceService;

    @BeforeEach
    void setUp() throws Exception {
        setupConfigWithRetry();
        setupHttpClient();
    }

    private void setupConfigWithRetry() {
        lenient().when(config.baseUrl()).thenReturn("https://confluence.test.com");
        lenient().when(config.username()).thenReturn("testuser");
        lenient().when(config.apiToken()).thenReturn("testtoken");
        lenient().when(config.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(1); // 1 retry for tests
        lenient().when(config.pagesLimit()).thenReturn(50);
        lenient().when(config.maxPages()).thenReturn(100);
        lenient().when(config.contentPath()).thenReturn("/content/");
        lenient().when(config.searchContentPath()).thenReturn("/content/search");
        lenient().when(config.spacePath()).thenReturn("/space");
        lenient().when(config.attachmentChildSuffix()).thenReturn("/child/attachment");
        lenient().when(config.defaultPageExpands()).thenReturn("body.storage,version,metadata,ancestors");
        lenient().when(config.defaultSpaceExpands()).thenReturn("permissions,metadata");
    }

    private void setupHttpClient() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        confluenceService = new ConfluenceHttpClientAdapter(config, objectMapper);

        Field retryExecutorField = ConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(confluenceService);

        Field retryExecutorHttpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        retryExecutorHttpClientField.setAccessible(true);
        retryExecutorHttpClientField.set(retryExecutor, httpClient);

        lenient().when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));
    }

    @Test
    void Should_RetryAndSucceed_When_FirstAttemptFails() throws Exception {
        // Arrange
        var r500 = mock(HttpResponse.class);
        when(r500.statusCode()).thenReturn(500);

        var r200 = mock(HttpResponse.class);
        when(r200.statusCode()).thenReturn(200);
        when(r200.body()).thenReturn(createPageJson("id-9", "T", "TEST", "<p>x</p>"));

        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r500))
            .thenReturn(CompletableFuture.completedFuture(r200));

        // Act
        Optional<ConfluencePage> opt = confluenceService.getPage("id-9").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isPresent();
        softly.assertAll();
    }

    private String createPageJson(String id, String title, String spaceKey, String content) {
        ObjectNode pageNode = JsonNodeFactory.instance.objectNode();
        pageNode.put("id", id);
        pageNode.put("title", title);

        ObjectNode spaceNode = JsonNodeFactory.instance.objectNode();
        spaceNode.put("key", spaceKey);
        pageNode.set("space", spaceNode);

        ObjectNode bodyNode = JsonNodeFactory.instance.objectNode();
        ObjectNode storageNode = JsonNodeFactory.instance.objectNode();
        storageNode.put("value", content);
        storageNode.put("representation", "storage");
        bodyNode.set("storage", storageNode);
        pageNode.set("body", bodyNode);

        ObjectNode versionNode = JsonNodeFactory.instance.objectNode();
        versionNode.put("number", 1);
        pageNode.set("version", versionNode);

        return pageNode.toString();
    }
}
