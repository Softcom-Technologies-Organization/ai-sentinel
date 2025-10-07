package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

@ExtendWith(MockitoExtension.class)
class ConfluenceAttachmentHttpClientAdapterTest {

    @Mock
    private HttpClient httpClient;

    private ConfluenceAttachmentClient confluenceAttachmentService;

    @BeforeEach
    void setUp() throws Exception {
        // Build a real config record instead of mocking the final record (Mockito can't mock records reliably)
        ConfluenceConfig config = new ConfluenceConfig(
            "https://confluence.test.com/",
            "testuser",
            "testtoken",
            "TEST",
            new ConfluenceConfig.ConnectionSettings(10_000, 10_000, 0, false, null),
            new ConfluenceConfig.PaginationSettings(50, 5),
            new ConfluenceConfig.ApiPaths(
                "/content/",
                "/content/search",
                "/space",
                "/child/attachment",
                "body.storage,version,metadata,ancestors",
                "permissions,metadata"
            ),
            new ConfluenceConfig.CacheSettings(300000, 5000),
            new ConfluenceConfig.PollingSettings(60000)
        );
        // No stubbing needed for record accessors; we built a concrete config above.

        final ObjectMapper mapper = new ObjectMapper();
        ConfluenceAttachmentHttpClientAdapter service = new ConfluenceAttachmentHttpClientAdapter(config, mapper);

        // Inject mocked HttpClient into the attachment service
        Field f = ConfluenceAttachmentHttpClientAdapter.class.getDeclaredField("httpClient");
        f.setAccessible(true);
        f.set(service, httpClient);
        this.confluenceAttachmentService = service;
    }

    @Test
    void getPageAttachments_success_mapping_and_url() throws Exception {
        String body = attachmentsJson(List.of(
            attachment("/download/path/file.pdf")
        ));
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(body);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));

        var list = confluenceAttachmentService.getPageAttachments("123").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list).hasSize(1);
        softly.assertThat(list.getFirst().name()).isEqualTo("file.pdf");
        softly.assertThat(list.getFirst().url())
            .isEqualTo("https://confluence.test.com/download/path/file.pdf");
        softly.assertAll();
    }

    @Test
    void getPageAttachments_404_500_and_parseError_returnEmpty() throws Exception {
        var r404 = mock(HttpResponse.class);
        when(r404.statusCode()).thenReturn(404);
        var r500 = mock(HttpResponse.class);
        when(r500.statusCode()).thenReturn(500);
        var rBad = mock(HttpResponse.class);
        when(rBad.statusCode()).thenReturn(200);
        when(rBad.body()).thenReturn("{bad");

        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r404))
            .thenReturn(CompletableFuture.completedFuture(r500))
            .thenReturn(CompletableFuture.completedFuture(rBad));

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(confluenceAttachmentService.getPageAttachments("1").get()).isEmpty();
        softly.assertThat(confluenceAttachmentService.getPageAttachments("1").get()).isEmpty();
        softly.assertThat(confluenceAttachmentService.getPageAttachments("1").get()).isEmpty();
        softly.assertAll();
    }

    private static ObjectNode attachment(String downloadPath) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("title", "file.pdf");
        ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("mediaType", "application/pdf");
        node.set("metadata", metadata);
        ObjectNode links = JsonNodeFactory.instance.objectNode();
        links.put("download", downloadPath);
        node.set("_links", links);
        return node;
    }

    private static String attachmentsJson(List<ObjectNode> attachments) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        attachments.forEach(results::add);
        root.set("results", results);
        return root.toString();
    }

    @Test
    void getPageAttachments_normalizesDownloadPathWithoutLeadingSlash() throws Exception {
        String body = attachmentsJson(List.of(
            attachment("download/path/file.pdf") // no leading slash
        ));
        var r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(body);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(r));
        var list = confluenceAttachmentService.getPageAttachments("123").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list.getFirst().url())
            .isEqualTo("https://confluence.test.com/download/path/file.pdf");
        softly.assertAll();
    }
}
