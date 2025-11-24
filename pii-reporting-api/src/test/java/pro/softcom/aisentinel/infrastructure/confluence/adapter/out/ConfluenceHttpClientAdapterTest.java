package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * Tests des scénarios "happy path" de haut niveau pour ConfluenceHttpClientAdapter.
 * 
 * Portée: récupération simple de pages, espaces, recherches basiques sans cas d'erreur complexe.
 * Les tests spécialisés (erreurs, pagination, retry, etc.) sont dans des classes dédiées.
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
        lenient().when(config.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");

        // Connection settings configuration
        var connectionSettings = mock(ConfluenceConfig.ConnectionSettings.class);
        lenient().when(connectionSettings.connectTimeout()).thenReturn(5000);
        lenient().when(connectionSettings.readTimeout()).thenReturn(10000);
        lenient().when(connectionSettings.maxRetries()).thenReturn(3);
        lenient().when(config.connectionSettings()).thenReturn(connectionSettings);
        lenient().when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        lenient().when(config.maxRetries()).thenReturn(3);

        // Pagination settings configuration
        var paginationSettings = mock(ConfluenceConfig.PaginationSettings.class);
        lenient().when(paginationSettings.pagesLimit()).thenReturn(50);
        lenient().when(paginationSettings.maxPages()).thenReturn(100);
        lenient().when(config.paginationSettings()).thenReturn(paginationSettings);
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
    void Should_ReturnPage_When_PageExists() throws Exception {
        // Arrange
        String pageId = "123456";
        String pageTitle = "Test Page";
        String pageContent = "<p>Test content</p>";

        String responseBody = createPageJson(pageId, pageTitle, "TEST", pageContent);

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

        softly.assertAll();
    }

    @Test
    void Should_ReturnPages_When_SearchingInSpace() throws Exception {
        // Arrange
        String spaceKey = "TEST";
        String query = "test";

        String responseBody = createSearchResultJson(List.of(
            new TestPage("page1", "Page 1", spaceKey, "<p>Content 1</p>"),
            new TestPage("page2", "Page 2", spaceKey, "<p>Content 2</p>")
        ));

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

        softly.assertAll();
    }

    @Test
    void Should_ReturnAllPages_When_RetrievingFromSpace() throws Exception {
        // Arrange
        String spaceKey = "TEST";

        String responseBody = createSpaceContentJson(List.of(
            new TestPage("page1", "Page 1", spaceKey, "<p>Content 1</p>"),
            new TestPage("page2", "Page 2", spaceKey, "<p>Content 2</p>")
        ));

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluencePage>> result = confluenceService.getAllPagesInSpace(spaceKey);
        List<ConfluencePage> pages = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);

        softly.assertThat(pages.getFirst().id()).isEqualTo("page1");
        softly.assertThat(pages.get(0).title()).isEqualTo("Page 1");
        softly.assertThat(pages.get(0).spaceKey()).isEqualTo(spaceKey);

        softly.assertThat(pages.get(1).id()).isEqualTo("page2");
        softly.assertThat(pages.get(1).title()).isEqualTo("Page 2");
        softly.assertThat(pages.get(1).spaceKey()).isEqualTo(spaceKey);

        softly.assertAll();
    }

    @Test
    void Should_ReturnSpace_When_SpaceExists() throws Exception {
        // Arrange
        String spaceKey = "TEST";
        String spaceName = "Test Space";
        String spaceDescription = "This is a test space";

        String responseBody = createSpaceJson(spaceKey, spaceName, spaceDescription);

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

        softly.assertAll();
    }

    @Test
    void Should_ReturnAllSpaces_When_RetrievingSpaces() throws Exception {
        // Arrange
        String responseBody = createSpacesResponseJson(List.of(
            new TestSpace("SPACE1", "Space 1", "Description 1"),
            new TestSpace("SPACE2", "Space 2", "Description 2")
        ));

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        // Act
        CompletableFuture<List<ConfluenceSpace>> result = confluenceService.getAllSpaces();
        List<ConfluenceSpace> spaces = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(spaces).hasSize(2);

        softly.assertThat(spaces.getFirst().key()).isEqualTo("SPACE1");
        softly.assertThat(spaces.get(0).name()).isEqualTo("Space 1");
        softly.assertThat(spaces.get(0).description()).isEqualTo("Description 1");

        softly.assertThat(spaces.get(1).key()).isEqualTo("SPACE2");
        softly.assertThat(spaces.get(1).name()).isEqualTo("Space 2");
        softly.assertThat(spaces.get(1).description()).isEqualTo("Description 2");

        softly.assertAll();
    }

    @Test
    void Should_ReturnUpdatedPage_When_UpdateSucceeds() throws Exception {
        // Arrange
        String pageId = "page-123";
        String pageTitle = "Updated Test Page";
        String spaceKey = "TEST";
        String pageContent = "<p>Updated test content</p>";

        ConfluencePage pageToUpdate = ConfluencePage.builder()
            .id(pageId)
            .title(pageTitle)
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent(pageContent))
            .build();

        String responseBody = createPageJson(pageId, pageTitle, spaceKey, pageContent);

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

        softly.assertAll();
    }

    @Test
    void Should_ReturnTrue_When_ConnectionSucceeds() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);

        // Act
        CompletableFuture<Boolean> result = confluenceService.testConnection();
        boolean isConnected = result.get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(isConnected).isTrue();

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
}
