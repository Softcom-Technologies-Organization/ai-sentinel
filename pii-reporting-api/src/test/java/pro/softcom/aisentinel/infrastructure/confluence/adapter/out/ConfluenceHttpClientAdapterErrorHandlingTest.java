package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ModifiedPageInfo;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de gestion d'erreurs pour ConfluenceHttpClientAdapter.
 * 
 * Portée: statuts HTTP non-2xx, timeouts, exceptions réseau, mapping vers ConfluenceException,
 * messages d'erreur, gestion des erreurs de parsing JSON.
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceHttpClientAdapterErrorHandlingTest {

    @Mock
    private ConfluenceConfig config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ConfluenceHttpClientAdapter confluenceService;

    @BeforeEach
    void setUp() throws Exception {
        setupConfig();
        setupHttpClient();
    }

    private void setupConfig() {
        lenient().when(config.baseUrl()).thenReturn("https://confluence.test.com");
        lenient().when(config.username()).thenReturn("testuser");
        lenient().when(config.apiToken()).thenReturn("testtoken");
        lenient().when(config.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");

        var connectionSettings = mock(ConfluenceConfig.ConnectionSettings.class);
        lenient().when(connectionSettings.connectTimeout()).thenReturn(5000);
        lenient().when(connectionSettings.readTimeout()).thenReturn(10000);
        lenient().when(connectionSettings.maxRetries()).thenReturn(0); // No retry for error tests
        lenient().when(config.connectionSettings()).thenReturn(connectionSettings);
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(0);

        var paginationSettings = mock(ConfluenceConfig.PaginationSettings.class);
        lenient().when(paginationSettings.pagesLimit()).thenReturn(50);
        lenient().when(paginationSettings.maxPages()).thenReturn(100);
        lenient().when(config.paginationSettings()).thenReturn(paginationSettings);
        lenient().when(config.pagesLimit()).thenReturn(50);
        lenient().when(config.maxPages()).thenReturn(100);

        var apiPaths = new ConfluenceConfig.ApiPaths(
            "/content/",
            "/content/search",
            "/space",
            "/child/attachment",
            "body.storage,version,metadata,ancestors",
            "permissions,metadata"
        );
        lenient().when(config.apiPaths()).thenReturn(apiPaths);
        lenient().when(config.contentPath()).thenReturn(apiPaths.contentPath());
        lenient().when(config.searchContentPath()).thenReturn(apiPaths.searchContentPath());
        lenient().when(config.spacePath()).thenReturn(apiPaths.spacePath());
        lenient().when(config.attachmentChildSuffix()).thenReturn(apiPaths.attachmentChildSuffix());
        lenient().when(config.defaultPageExpands()).thenReturn(apiPaths.defaultPageExpands());
        lenient().when(config.defaultSpaceExpands()).thenReturn(apiPaths.defaultSpaceExpands());
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
    void Should_ReturnEmpty_When_PageNotFound() throws Exception {
        // Arrange
        String pageId = "nonexistent";
        when(httpResponse.statusCode()).thenReturn(404);

        // Act
        CompletableFuture<Optional<ConfluencePage>> result = confluenceService.getPage(pageId);
        Optional<ConfluencePage> pageOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pageOpt).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_PageRetrievalFails() throws Exception {
        // Arrange
        String pageId = "123";
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        CompletableFuture<Optional<ConfluencePage>> result = confluenceService.getPage(pageId);
        Optional<ConfluencePage> pageOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pageOpt).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_PageParsingFails() throws Exception {
        // Arrange
        String pageId = "123";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        Optional<ConfluencePage> opt = confluenceService.getPage(pageId).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_SearchReturnsNon200() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        List<ConfluencePage> list = confluenceService.searchPages("TEST", "query").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_SearchParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        List<ConfluencePage> list = confluenceService.searchPages("TEST", "query").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetAllPagesParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{not-json");

        // Act
        List<ConfluencePage> pages = confluenceService.getAllPagesInSpace("TEST").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetAllPagesMissingPageNode() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");

        // Act
        List<ConfluencePage> pages = confluenceService.getAllPagesInSpace("TEST").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetSpaceNotFound() throws Exception {
        // Arrange
        String spaceKey = "NONEXISTENT";
        when(httpResponse.statusCode()).thenReturn(404);

        // Act
        CompletableFuture<Optional<ConfluenceSpace>> result = confluenceService.getSpace(spaceKey);
        Optional<ConfluenceSpace> spaceOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaceOpt).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetSpaceParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        Optional<ConfluenceSpace> opt = confluenceService.getSpace("KEY").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetAllSpacesReturnsNon200() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        List<ConfluenceSpace> spaces = confluenceService.getAllSpaces().get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_GetAllSpacesParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        List<ConfluenceSpace> spaces = confluenceService.getAllSpaces().get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ThrowException_When_UpdatePageReturnsNon200() {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(409);

        ConfluencePage page = ConfluencePage.builder()
            .id("idx")
            .title("T")
            .spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("<p>x</p>"))
            .build();

        // Act & Assert
        try {
            confluenceService.updatePage(page).join();
        } catch (java.util.concurrent.CompletionException ce) {
            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(ce.getCause()).isInstanceOf(ConfluenceApiException.class);
            softly.assertAll();
        }
    }

    @Test
    void Should_ReturnFalse_When_ConnectionFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        CompletableFuture<Boolean> result = confluenceService.testConnection();
        boolean isConnected = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(isConnected).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_ReturnFalse_When_ConnectionFailsExceptionally() {
        // Arrange
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        // Act
        boolean ok = confluenceService.testConnection().join();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(ok).isFalse();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedPagesReturnsNon200() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedPagesParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedPagesRequestFailsExceptionally() {
        // Arrange
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).join();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedAttachmentsReturnsNon200() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(500);

        // Act
        List<ModifiedAttachmentInfo> attachments = confluenceService
            .getModifiedAttachmentsSince("TEST", Instant.parse("2025-03-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(attachments).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedAttachmentsParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{invalid");

        // Act
        List<ModifiedAttachmentInfo> attachments = confluenceService
            .getModifiedAttachmentsSince("TEST", Instant.parse("2025-03-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(attachments).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_ModifiedAttachmentsRequestFailsExceptionally() {
        // Arrange
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        // Act
        List<ModifiedAttachmentInfo> attachments = confluenceService
            .getModifiedAttachmentsSince("TEST", Instant.parse("2025-03-01T00:00:00Z")).join();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(attachments).isEmpty();
        softly.assertAll();
    }
}
