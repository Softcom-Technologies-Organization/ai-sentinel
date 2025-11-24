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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ModifiedPageInfo;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * Tests de parsing de contenu pour ConfluenceHttpClientAdapter.
 * 
 * Portée: parsing JSON des pages (body.storage, version, metadata), mapping vers
 * ConfluencePage/ModifiedPageInfo/ModifiedAttachmentInfo, filtrage selon types, gestion des champs manquants.
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceHttpClientAdapterContentParsingTest {

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
        lenient().when(connectionSettings.maxRetries()).thenReturn(0);
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
    void Should_ReturnModifiedPages_When_CqlReturnsVersionWhenDates() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        String json = createCqlModifiedPagesJson(
            pageResultNode("p1", "Title 1", "page", "2025-01-02T03:04:05Z", null),
            pageResultNode("p2", "Title 2", "page", "2025-01-03T04:05:06Z", null)
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);
        softly.assertThat(pages.getFirst().pageId()).isEqualTo("p1");
        softly.assertThat(pages.get(0).title()).isEqualTo("Title 1");
        softly.assertThat(pages.get(0).lastModified()).isEqualTo(Instant.parse("2025-01-02T03:04:05Z"));
        softly.assertThat(pages.get(1).pageId()).isEqualTo("p2");
        softly.assertThat(pages.get(1).title()).isEqualTo("Title 2");
        softly.assertThat(pages.get(1).lastModified()).isEqualTo(Instant.parse("2025-01-03T04:05:06Z"));
        softly.assertAll();
    }

    @Test
    void Should_UseHistoryFallback_When_VersionWhenMissing() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        String json = createCqlModifiedPagesJson(
            pageResultNode("p3", "T3", "page", null, "2025-02-01T10:00:00Z")
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(1);
        softly.assertThat(pages.get(0).pageId()).isEqualTo("p3");
        softly.assertThat(pages.get(0).lastModified()).isEqualTo(Instant.parse("2025-02-01T10:00:00Z"));
        softly.assertAll();
    }

    @Test
    void Should_FilterOutInvalidEntries_When_WrongTypeMissingIdOrUnparsableDate() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        String json = createCqlModifiedPagesJson(
            pageResultNode("p4", null, "page", "2025-03-01T00:00:00Z", null),
            pageResultNode(null, "NoId", "page", "2025-03-02T00:00:00Z", null),
            pageResultNode("pX", "BadDate", "page", "not-a-date", null),
            pageResultNode("a1", "Attachment", "attachment", "2025-03-03T00:00:00Z", null)
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert - seule p4 est valide (avec titre par défaut "Untitled")
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(1);
        softly.assertThat(pages.getFirst().pageId()).isEqualTo("p4");
        softly.assertThat(pages.getFirst().title()).isEqualTo("Untitled");
        softly.assertAll();
    }

    @Test
    void Should_LogErrorAndSkipEntry_When_DateParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);

        String json = createCqlModifiedPagesJson(
            pageResultNode("p1", "Valid Page", "page", "2025-01-02T03:04:05Z", null),
            pageResultNode("p2", "Invalid Date Page", "page", "not-a-valid-date", null),
            pageResultNode("p3", "Another Valid Page", "page", "2025-01-03T04:05:06Z", null)
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert - devrait retourner seulement les pages avec dates valides
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(2);
        softly.assertThat(pages.get(0).pageId()).isEqualTo("p1");
        softly.assertThat(pages.get(0).title()).isEqualTo("Valid Page");
        softly.assertThat(pages.get(0).lastModified()).isEqualTo(Instant.parse("2025-01-02T03:04:05Z"));
        softly.assertThat(pages.get(1).pageId()).isEqualTo("p3");
        softly.assertThat(pages.get(1).title()).isEqualTo("Another Valid Page");
        softly.assertThat(pages.get(1).lastModified()).isEqualTo(Instant.parse("2025-01-03T04:05:06Z"));
        softly.assertAll();
    }

    @Test
    void Should_UseHistoryFallback_When_VersionDateInvalid() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);

        // Page avec version.when invalide mais history.lastUpdated.when valide
        String json = createCqlModifiedPagesJson(
            pageResultNode("p1", "Fallback Page", "page", "bad-format", "2025-02-01T10:00:00Z")
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedPageInfo> pages = confluenceService
            .getModifiedPagesSince("TEST", Instant.parse("2025-01-01T00:00:00Z")).get();

        // Assert - devrait utiliser history en fallback
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pages).hasSize(1);
        softly.assertThat(pages.getFirst().pageId()).isEqualTo("p1");
        softly.assertThat(pages.getFirst().lastModified()).isEqualTo(Instant.parse("2025-02-01T10:00:00Z"));
        softly.assertAll();
    }

    @Test
    void Should_ReturnModifiedAttachments_When_CqlReturnsResults() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        String json = createCqlModifiedAttachmentsJson(
            pageResultNode("att1", "Invoice.pdf", "attachment", "2025-04-01T00:00:00Z", null),
            pageResultNode("att2", null, "attachment", null, "2025-04-02T01:02:03Z")
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedAttachmentInfo> attachments = confluenceService
            .getModifiedAttachmentsSince("TEST", Instant.parse("2025-03-01T00:00:00Z")).get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(attachments).hasSize(2);
        softly.assertThat(attachments.getFirst().attachmentId()).isEqualTo("att1");
        softly.assertThat(attachments.get(0).title()).isEqualTo("Invoice.pdf");
        softly.assertThat(attachments.get(0).lastModified()).isEqualTo(Instant.parse("2025-04-01T00:00:00Z"));
        softly.assertThat(attachments.get(1).attachmentId()).isEqualTo("att2");
        softly.assertThat(attachments.get(1).title()).isEqualTo("Unnamed");
        softly.assertThat(attachments.get(1).lastModified()).isEqualTo(Instant.parse("2025-04-02T01:02:03Z"));
        softly.assertAll();
    }

    @Test
    void Should_LogErrorAndSkipAttachment_When_AttachmentDateParsingFails() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);

        String json = createCqlModifiedAttachmentsJson(
            pageResultNode("att1", "Valid.pdf", "attachment", "2025-04-01T00:00:00Z", null),
            pageResultNode("att2", "Invalid.pdf", "attachment", "invalid-date-format", null),
            pageResultNode("att3", "Another Valid.pdf", "attachment", "2025-04-02T01:02:03Z", null)
        );
        when(httpResponse.body()).thenReturn(json);

        // Act
        List<ModifiedAttachmentInfo> attachments = confluenceService
            .getModifiedAttachmentsSince("TEST", Instant.parse("2025-03-01T00:00:00Z")).get();

        // Assert - devrait retourner seulement les attachments avec dates valides
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(attachments).hasSize(2);
        softly.assertThat(attachments.getFirst().attachmentId()).isEqualTo("att1");
        softly.assertThat(attachments.get(0).title()).isEqualTo("Valid.pdf");
        softly.assertThat(attachments.get(0).lastModified()).isEqualTo(Instant.parse("2025-04-01T00:00:00Z"));
        softly.assertThat(attachments.get(1).attachmentId()).isEqualTo("att3");
        softly.assertThat(attachments.get(1).title()).isEqualTo("Another Valid.pdf");
        softly.assertThat(attachments.get(1).lastModified()).isEqualTo(Instant.parse("2025-04-02T01:02:03Z"));
        softly.assertAll();
    }

    @Test
    void Should_ParseSpaceWithVariousStates_When_CqlReturnsMultipleFormats() throws Exception {
        // Arrange
        when(httpResponse.statusCode()).thenReturn(200);
        
        String json = createSpaceJson("S1", "Space1", "desc");
        when(httpResponse.body()).thenReturn(json);

        // Act
        Optional<ConfluenceSpace> opt = confluenceService.getSpaceById("S1").get();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(opt).isPresent();
        softly.assertThat(opt.get().key()).isEqualTo("S1");
        softly.assertThat(opt.get().name()).isEqualTo("Space1");
        softly.assertThat(opt.get().description()).isEqualTo("desc");
        softly.assertAll();
    }

    // Méthodes utilitaires pour créer des JSON de test
    private String createCqlModifiedPagesJson(ObjectNode... items) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        for (ObjectNode n : items) {
            results.add(n);
        }
        root.set("results", results);
        root.put("size", results.size());
        return root.toString();
    }

    private String createCqlModifiedAttachmentsJson(ObjectNode... items) {
        return createCqlModifiedPagesJson(items);
    }

    private ObjectNode pageResultNode(String id, String title, String type, String versionWhen, String historyWhen) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (id != null) node.put("id", id);
        if (title != null) node.put("title", title);
        if (type != null) node.put("type", type);
        if (versionWhen != null) {
            ObjectNode version = JsonNodeFactory.instance.objectNode();
            version.put("when", versionWhen);
            node.set("version", version);
        }
        if (historyWhen != null) {
            ObjectNode history = JsonNodeFactory.instance.objectNode();
            ObjectNode lastUpdated = JsonNodeFactory.instance.objectNode();
            lastUpdated.put("when", historyWhen);
            history.set("lastUpdated", lastUpdated);
            node.set("history", history);
        }
        return node;
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
}
