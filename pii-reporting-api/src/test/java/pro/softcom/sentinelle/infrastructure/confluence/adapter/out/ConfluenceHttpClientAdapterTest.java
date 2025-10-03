package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * Unit tests for ConfluenceServiceImpl.
 * Uses SoftAssertions, AssertJ and Mockito to test service methods.
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceHttpClientAdapterTest {

    @Mock
    private ConfluenceConfig config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ConfluenceHttpClientAdapter confluenceService;

    @BeforeEach
    void setUp() throws Exception {
        // Test configuration - use lenient() to avoid unnecessary stubbing errors
        lenient().when(config.baseUrl()).thenReturn("https://confluence.test.com");
        lenient().when(config.username()).thenReturn("testuser");
        lenient().when(config.apiToken()).thenReturn("testtoken");
        lenient().when(config.spaceKey()).thenReturn("TEST");
        lenient().when(config.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");

        // Connection settings configuration
        var connectionSettings = mock(ConfluenceConfig.ConnectionSettings.class);
        lenient().when(connectionSettings.connectTimeout()).thenReturn(5000);
        lenient().when(connectionSettings.readTimeout()).thenReturn(10000);
        lenient().when(connectionSettings.maxRetries()).thenReturn(3);
        lenient().when(config.connectionSettings()).thenReturn(connectionSettings);
        // The service uses scalar methods from ConfluenceConnectionConfig contract,
        // so these values must be stubbed directly on the main mock
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(3);

        // Pagination settings configuration
        var paginationSettings = mock(ConfluenceConfig.PaginationSettings.class);
        lenient().when(paginationSettings.pagesLimit()).thenReturn(50);
        lenient().when(paginationSettings.maxPages()).thenReturn(100);
        lenient().when(config.paginationSettings()).thenReturn(paginationSettings);
        // Corresponding scalar methods used by the adapter
        lenient().when(config.pagesLimit()).thenReturn(50);
        lenient().when(config.maxPages()).thenReturn(100);

        // Create real ObjectMapper
        final ObjectMapper objectMapper = new ObjectMapper();

        // Stub API paths used by service
        var apiPaths = new ConfluenceConfig.ApiPaths(
            "/content/",
            "/content/search",
            "/space",
            "/child/attachment",
            "body.storage,version,metadata,ancestors",
            "permissions,metadata"
        );
        lenient().when(config.apiPaths()).thenReturn(apiPaths);
        // And direct stubs of scalar getters used by the adapter
        lenient().when(config.contentPath()).thenReturn(apiPaths.contentPath());
        lenient().when(config.searchContentPath()).thenReturn(apiPaths.searchContentPath());
        lenient().when(config.spacePath()).thenReturn(apiPaths.spacePath());
        lenient().when(config.attachmentChildSuffix()).thenReturn(apiPaths.attachmentChildSuffix());
        lenient().when(config.defaultPageExpands()).thenReturn(apiPaths.defaultPageExpands());
        lenient().when(config.defaultSpaceExpands()).thenReturn(apiPaths.defaultSpaceExpands());

        // Create service with mocks
        confluenceService = new ConfluenceHttpClientAdapter(config, objectMapper);

        // Inject mocked HttpClient into HttpRetryExecutor via reflection
        Field retryExecutorField = ConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(confluenceService);
        
        Field retryExecutorHttpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        retryExecutorHttpClientField.setAccessible(true);
        retryExecutorHttpClientField.set(retryExecutor, httpClient);

        // Default HttpClient configuration
        lenient().when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
            .thenReturn(CompletableFuture.completedFuture(httpResponse));
    }

    @Test
    void getPage_ReturnsPage_WhenPageExists() throws Exception {
        // Arrange
        String pageId = "123456";
        String pageTitle = "Test Page";
        String pageContent = "<p>Test content</p>";

        // Create JSON response for a page
        String responseBody = createPageJson(pageId, pageTitle, "TEST", pageContent);

        // Configure HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<Optional<ConfluencePage>> result = confluenceService.getPage(pageId);
        Optional<ConfluencePage> pageOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pageOpt).isPresent();

        ConfluencePage page = pageOpt.get();
        softly.assertThat(page.id()).isEqualTo(pageId);
        softly.assertThat(page.title()).isEqualTo(pageTitle);
        softly.assertThat(page.spaceKey()).isEqualTo("TEST");
        softly.assertThat(page.content().body()).isEqualTo(pageContent);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/content/" + pageId)
            .contains("expand=body.storage,version,metadata,ancestors");

        softly.assertAll();
    }

    @Test
    void getPage_ReturnsEmpty_WhenPageDoesNotExist() throws Exception {
        // Arrange
        String pageId = "nonexistent";

        // Configure the HTTP response for a page not found
        when(httpResponse.statusCode()).thenReturn(404);

        // Act
        CompletableFuture<Optional<ConfluencePage>> result = confluenceService.getPage(pageId);
        Optional<ConfluencePage> pageOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pageOpt).isEmpty();

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/content/" + pageId);

        softly.assertAll();
    }

    @Test
    void searchPages_ReturnsPages_WhenPagesExist() throws Exception {
        // Arrange
        String spaceKey = "TEST";
        String query = "test";

        // Create a JSON response for a search
        String responseBody = createSearchResultJson(List.of(
            new TestPage("page1", "Page 1", spaceKey, "<p>Content 1</p>"),
            new TestPage("page2", "Page 2", spaceKey, "<p>Content 2</p>")
        ));

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluencePage>> result = confluenceService.searchPages(spaceKey, query);
        List<ConfluencePage> pages = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);

        softly.assertThat(pages.getFirst().id()).isEqualTo("page1");
        softly.assertThat(pages.getFirst().title()).isEqualTo("Page 1");
        softly.assertThat(pages.getFirst().spaceKey()).isEqualTo(spaceKey);

        softly.assertThat(pages.get(1).id()).isEqualTo("page2");
        softly.assertThat(pages.get(1).title()).isEqualTo("Page 2");
        softly.assertThat(pages.get(1).spaceKey()).isEqualTo(spaceKey);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/content/search")
            .contains("cql=")
            .contains("space%3D%27" + spaceKey + "%27")
            .contains("text+%7E+%27" + query + "%27");

        softly.assertAll();
    }

    @Test
    void searchPages_CallsGetAllPagesInSpace_WhenQueryIsEmpty() throws Exception {
        // Arrange
        String spaceKey = "TEST";
        String query = "";

        // Create a JSON response for getAllPagesInSpace
        String responseBody = createSpaceContentJson(List.of(
            new TestPage("page1", "Page 1", spaceKey, "<p>Content 1</p>"),
            new TestPage("page2", "Page 2", spaceKey, "<p>Content 2</p>")
        ));

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluencePage>> result = confluenceService.searchPages(spaceKey, query);
        List<ConfluencePage> pages = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space/" + spaceKey + "/content")
            .contains("expand=version")
            .contains("limit=")
            .contains("start=");

        softly.assertAll();
    }

    @Test
    void getAllPagesInSpace_ReturnsPages_WhenPagesExist() throws Exception {
        // Arrange
        String spaceKey = "TEST";

        // Create a JSON response for getAllPagesInSpace
        String responseBody = createSpaceContentJson(List.of(
            new TestPage("page1", "Page 1", spaceKey, "<p>Content 1</p>"),
            new TestPage("page2", "Page 2", spaceKey, "<p>Content 2</p>")
        ));

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluencePage>> result = confluenceService.getAllPagesInSpace(spaceKey);
        List<ConfluencePage> pages = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);

        softly.assertThat(pages.get(0).id()).isEqualTo("page1");
        softly.assertThat(pages.get(0).title()).isEqualTo("Page 1");
        softly.assertThat(pages.get(0).spaceKey()).isEqualTo(spaceKey);

        softly.assertThat(pages.get(1).id()).isEqualTo("page2");
        softly.assertThat(pages.get(1).title()).isEqualTo("Page 2");
        softly.assertThat(pages.get(1).spaceKey()).isEqualTo(spaceKey);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space/" + spaceKey + "/content")
            .contains("expand=version")
            .contains("limit=")
            .contains("start=");

        softly.assertAll();
    }

    @Test
    void getSpace_ReturnsSpace_WhenSpaceExists() throws Exception {
        // Arrange
        String spaceKey = "TEST";
        String spaceName = "Test Space";
        String spaceDescription = "This is a test space";

        // Create a JSON response for un espace
        String responseBody = createSpaceJson(spaceKey, spaceName, spaceDescription);

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<Optional<ConfluenceSpace>> result = confluenceService.getSpace(spaceKey);
        Optional<ConfluenceSpace> spaceOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaceOpt).isPresent();

        ConfluenceSpace space = spaceOpt.get();
        softly.assertThat(space.key()).isEqualTo(spaceKey);
        softly.assertThat(space.name()).isEqualTo(spaceName);
        softly.assertThat(space.description()).isEqualTo(spaceDescription);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space/" + spaceKey)
            .contains("expand=permissions,metadata");

        softly.assertAll();
    }

    @Test
    void getSpace_ReturnsEmpty_WhenSpaceDoesNotExist() throws Exception {
        // Arrange
        String spaceKey = "NONEXISTENT";

        // Configure the HTTP response for a space non trouvé
        when(httpResponse.statusCode()).thenReturn(404);

        // Act
        CompletableFuture<Optional<ConfluenceSpace>> result = confluenceService.getSpace(spaceKey);
        Optional<ConfluenceSpace> spaceOpt = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaceOpt).isEmpty();

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space/" + spaceKey);

        softly.assertAll();
    }

    @Test
    void testConnection_ReturnsTrue_WhenConnectionSucceeds() throws Exception {
        // Arrange
        // Configure the HTTP response for a successful connection
        when(httpResponse.statusCode()).thenReturn(200);

        // Act
        CompletableFuture<Boolean> result = confluenceService.testConnection();
        boolean isConnected = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(isConnected).isTrue();

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space");

        softly.assertAll();
    }

    @Test
    void testConnection_ReturnsFalse_WhenConnectionFails() throws Exception {
        // Arrange
        // Configure the HTTP response for a failed connection
        when(httpResponse.statusCode()).thenReturn(500);

        // Create a new config with maxRetries=0 for this test
        var testConfig = mock(ConfluenceConfig.class);
        lenient().when(testConfig.baseUrl()).thenReturn("https://confluence.test.com");
        lenient().when(testConfig.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");
        lenient().when(testConfig.username()).thenReturn("testuser");
        lenient().when(testConfig.apiToken()).thenReturn("testtoken");
        lenient().when(testConfig.connectTimeout()).thenReturn(5000);
        lenient().when(testConfig.readTimeout()).thenReturn(10000);
        lenient().when(testConfig.maxRetries()).thenReturn(0); // No retry
        lenient().when(testConfig.pagesLimit()).thenReturn(50);
        lenient().when(testConfig.maxPages()).thenReturn(100);
        lenient().when(testConfig.contentPath()).thenReturn("/content/");
        lenient().when(testConfig.searchContentPath()).thenReturn("/content/search");
        lenient().when(testConfig.spacePath()).thenReturn("/space");
        lenient().when(testConfig.attachmentChildSuffix()).thenReturn("/child/attachment");
        lenient().when(testConfig.defaultPageExpands()).thenReturn("body.storage,version,metadata,ancestors");
        lenient().when(testConfig.defaultSpaceExpands()).thenReturn("permissions,metadata");

        // Create a new adapter with maxRetries=0
        final ObjectMapper objectMapper = new ObjectMapper();
        var testService = new ConfluenceHttpClientAdapter(testConfig, objectMapper);

        // Inject the mocked HttpClient into HttpRetryExecutor via reflection
        Field retryExecutorField = ConfluenceHttpClientAdapter.class.getDeclaredField("retryExecutor");
        retryExecutorField.setAccessible(true);
        Object retryExecutor = retryExecutorField.get(testService);
        
        Field retryExecutorHttpClientField = retryExecutor.getClass().getDeclaredField("httpClient");
        retryExecutorHttpClientField.setAccessible(true);
        retryExecutorHttpClientField.set(retryExecutor, httpClient);

        // Act
        CompletableFuture<Boolean> result = testService.testConnection();
        boolean isConnected = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(isConnected).isFalse();

        // Verify that the HTTP request was sent correctly (une seule fois, No retry)
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space");

        softly.assertAll();
    }

    @Test
    void getAllSpaces_ReturnsSpaces_WhenSpacesExist() throws Exception {
        // Arrange
        // Create a JSON response for une liste of spaces
        String responseBody = createSpacesResponseJson(List.of(
            new TestSpace("SPACE1", "Space 1", "Description 1"),
            new TestSpace("SPACE2", "Space 2", "Description 2")
        ));

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluenceSpace>> result = confluenceService.getAllSpaces();
        List<ConfluenceSpace> spaces = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).hasSize(2);

        softly.assertThat(spaces.get(0).key()).isEqualTo("SPACE1");
        softly.assertThat(spaces.get(0).name()).isEqualTo("Space 1");
        softly.assertThat(spaces.get(0).description()).isEqualTo("Description 1");

        softly.assertThat(spaces.get(1).key()).isEqualTo("SPACE2");
        softly.assertThat(spaces.get(1).name()).isEqualTo("Space 2");
        softly.assertThat(spaces.get(1).description()).isEqualTo("Description 2");

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/space")
            .contains("expand=permissions,metadata");

        softly.assertAll();
    }

    @Test
    void updatePage_ReturnsUpdatedPage_WhenUpdateSucceeds() throws Exception {
        // Arrange
        String pageId = "page-123";
        String pageTitle = "Updated Test Page";
        String spaceKey = "TEST";
        String pageContent = "<p>Updated test content</p>";

        // Create the page to update
        ConfluencePage pageToUpdate = ConfluencePage.builder()
            .id(pageId)
            .title(pageTitle)
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent(pageContent))
            .build();

        // Create a JSON response for an updated page
        String responseBody = createPageJson(pageId, pageTitle, spaceKey, pageContent);

        // Configure the HTTP response
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<ConfluencePage> result = confluenceService.updatePage(pageToUpdate);
        ConfluencePage updatedPage = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(updatedPage.id()).isEqualTo(pageId);
        softly.assertThat(updatedPage.title()).isEqualTo(pageTitle);
        softly.assertThat(updatedPage.spaceKey()).isEqualTo(spaceKey);
        softly.assertThat(updatedPage.content().body()).isEqualTo(pageContent);

        // Verify that the HTTP request was sent correctly
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        softly.assertThat(capturedRequest.uri().toString())
            .contains("/content/" + pageId);
        softly.assertThat(capturedRequest.method()).isEqualTo("PUT");

        softly.assertAll();
    }

    // Classes utilitaires pour les tests
    private record TestPage(String id, String title, String spaceKey, String content) {}
    private record TestSpace(String key, String name, String description) {}

    // Méthodes utilitaires pour créer des JSON de test
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

    private String createSearchResultJson(List<TestPage> pages) {
        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
        ArrayNode resultsNode = JsonNodeFactory.instance.arrayNode();

        for (TestPage page : pages) {
            ObjectNode pageNode = JsonNodeFactory.instance.objectNode();
            pageNode.put("id", page.id());
            pageNode.put("title", page.title());

            ObjectNode spaceNode = JsonNodeFactory.instance.objectNode();
            spaceNode.put("key", page.spaceKey());
            pageNode.set("space", spaceNode);

            ObjectNode bodyNode = JsonNodeFactory.instance.objectNode();
            ObjectNode storageNode = JsonNodeFactory.instance.objectNode();
            storageNode.put("value", page.content());
            storageNode.put("representation", "storage");
            bodyNode.set("storage", storageNode);
            pageNode.set("body", bodyNode);

            ObjectNode versionNode = JsonNodeFactory.instance.objectNode();
            versionNode.put("number", 1);
            pageNode.set("version", versionNode);

            resultsNode.add(pageNode);
        }

        rootNode.set("results", resultsNode);
        rootNode.put("size", pages.size());

        return rootNode.toString();
    }

    private String createSpaceContentJson(List<TestPage> pages) {
        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
        ObjectNode pageNode = JsonNodeFactory.instance.objectNode();
        ArrayNode resultsNode = JsonNodeFactory.instance.arrayNode();

        for (TestPage page : pages) {
            ObjectNode pageItemNode = JsonNodeFactory.instance.objectNode();
            pageItemNode.put("id", page.id());
            pageItemNode.put("title", page.title());

            ObjectNode spaceNode = JsonNodeFactory.instance.objectNode();
            spaceNode.put("key", page.spaceKey());
            pageItemNode.set("space", spaceNode);

            ObjectNode versionNode = JsonNodeFactory.instance.objectNode();
            versionNode.put("number", 1);
            pageItemNode.set("version", versionNode);

            resultsNode.add(pageItemNode);
        }

        pageNode.set("results", resultsNode);
        pageNode.put("size", pages.size());
        rootNode.set("page", pageNode);

        return rootNode.toString();
    }

    private String createSpaceJson(String key, String name, String description) {
        ObjectNode spaceNode = JsonNodeFactory.instance.objectNode();
        spaceNode.put("key", key);
        spaceNode.put("name", name);
        spaceNode.put("type", "global");
        spaceNode.put("status", "current");

        // Create the correct description structure
        ObjectNode descriptionNode = JsonNodeFactory.instance.objectNode();
        ObjectNode plainNode = JsonNodeFactory.instance.objectNode();
        plainNode.put("value", description);
        plainNode.put("representation", "plain");
        descriptionNode.set("plain", plainNode);

        ObjectNode viewNode = JsonNodeFactory.instance.objectNode();
        viewNode.put("value", "<p>" + description + "</p>");
        viewNode.put("representation", "view");
        descriptionNode.set("view", viewNode);

        spaceNode.set("description", descriptionNode);

        return spaceNode.toString();
    }

    private String createSpacesResponseJson(List<TestSpace> spaces) {
        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
        ArrayNode resultsNode = JsonNodeFactory.instance.arrayNode();

        for (TestSpace space : spaces) {
            ObjectNode spaceNode = JsonNodeFactory.instance.objectNode();
            spaceNode.put("key", space.key());
            spaceNode.put("name", space.name());
            spaceNode.put("type", "global");
            spaceNode.put("status", "current");

            // Create the correct description structure
            ObjectNode descriptionNode = JsonNodeFactory.instance.objectNode();
            ObjectNode plainNode = JsonNodeFactory.instance.objectNode();
            plainNode.put("value", space.description());
            plainNode.put("representation", "plain");
            descriptionNode.set("plain", plainNode);

            ObjectNode viewNode = JsonNodeFactory.instance.objectNode();
            viewNode.put("value", "<p>" + space.description() + "</p>");
            viewNode.put("representation", "view");
            descriptionNode.set("view", viewNode);

            spaceNode.set("description", descriptionNode);

            resultsNode.add(spaceNode);
        }

        rootNode.set("results", resultsNode);
        rootNode.put("size", spaces.size());

        return rootNode.toString();
    }

    @Test
    void getPage_ReturnsEmpty_When_ParseError() throws Exception {
        String pageId = "123";
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{invalid");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        Optional<ConfluencePage> opt = confluenceService.getPage(pageId).get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isEmpty();
        softly.assertAll();
    }

    @Test
    void searchPages_ReturnsEmpty_When_Non200() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluencePage> list = confluenceService.searchPages("TEST", "query").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list).isEmpty();
        softly.assertAll();
    }

    @Test
    void searchPages_ReturnsEmpty_When_ParseError() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{invalid");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluencePage> list = confluenceService.searchPages("TEST", "query").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list).isEmpty();
        softly.assertAll();
    }

    @Test
    void getAllPagesInSpace_ReturnsEmpty_When_ParseError() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{not-json");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluencePage> pages = confluenceService.getAllPagesInSpace("TEST").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void getAllPagesInSpace_ReturnsEmpty_When_MissingPageNode() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{} ");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluencePage> pages = confluenceService.getAllPagesInSpace("TEST").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).isEmpty();
        softly.assertAll();
    }

    @Test
    void getAllSpaces_ReturnsEmpty_When_Non200() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(500);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluenceSpace> spaces = confluenceService.getAllSpaces().get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).isEmpty();
        softly.assertAll();
    }

    @Test
    void getAllSpaces_ReturnsEmpty_When_ParseError() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{invalid");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        List<ConfluenceSpace> spaces = confluenceService.getAllSpaces().get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).isEmpty();
        softly.assertAll();
    }

    @Test
    void updatePage_ShouldThrow_When_Non200() {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(409);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        ConfluencePage page = ConfluencePage.builder()
            .id("idx").title("T").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("<p>x</p>")).build();
        try {
            confluenceService.updatePage(page).join();
        } catch (java.util.concurrent.CompletionException ce) {
            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(ce.getCause()).isInstanceOf(ConfluenceApiException.class);
            softly.assertAll();
        }
    }

    @Test
    void getSpace_ReturnsEmpty_When_ParseError() throws Exception {
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{invalid");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        Optional<ConfluenceSpace> opt = confluenceService.getSpace("KEY").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isEmpty();
        softly.assertAll();
    }

    @Test
    void getSpaceById_Success_ParseError_And_Non200() throws Exception {
        var ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn(createSpaceJson("S1", "Space1", "desc"));
        var badJson = mock(HttpResponse.class);
        when(badJson.statusCode()).thenReturn(200);
        when(badJson.body()).thenReturn("{invalid");
        var notOk = mock(HttpResponse.class);
        when(notOk.statusCode()).thenReturn(404);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(ok))
            .thenReturn(CompletableFuture.completedFuture(badJson))
            .thenReturn(CompletableFuture.completedFuture(notOk));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(confluenceService.getSpaceById("S1").get()).isPresent();
        softly.assertThat(confluenceService.getSpaceById("S1").get()).isEmpty();
        softly.assertThat(confluenceService.getSpaceById("S1").get()).isEmpty();
        softly.assertAll();
    }

    @Test
    void getPage_ShouldRetryOn500_ThenSucceed() throws Exception {
        // Ensure only 1 retry to speed up the test
        lenient().when(config.maxRetries()).thenReturn(1);
        var r500 = mock(HttpResponse.class);
        when(r500.statusCode()).thenReturn(500);
        var r200 = mock(HttpResponse.class);
        when(r200.statusCode()).thenReturn(200);
        when(r200.body()).thenReturn(createPageJson("id-9", "T", "TEST", "<p>x</p>"));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r500))
            .thenReturn(CompletableFuture.completedFuture(r200));
        Optional<ConfluencePage> opt = confluenceService.getPage("id-9").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isPresent();
        softly.assertAll();
    }

    @Test
    void testConnection_ReturnsFalse_When_ExceptionallyFailed() {
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        boolean ok = confluenceService.testConnection().join();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(ok).isFalse();
        softly.assertAll();
    }
}
