package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfig;

/**
 * Unit tests for ConfluenceDownloadServiceImpl.
 * Uses Mockito for stubbing, AssertJ for assertions and SoftAssertions for grouped verifications.
 */@ExtendWith(MockitoExtension.class)
class ConfluenceAttachmentHttpDownloaderAdapterTest {

    @SuppressWarnings("unchecked")
    private static <T> HttpResponse<T> mockResponse(int status, T body) {
        HttpResponse<T> r = (HttpResponse<T>) mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    private ConfluenceConfig config;
    private ObjectMapper objectMapper;
    private ConfluenceAttachmentHttpDownloaderAdapter service;
    private HttpClient httpClient; // will be injected via reflection

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        config = new ConfluenceConfig(
                "https://example.atlassian.net", // baseUrl
                "user@example.com",
                "token-123",
                new ConfluenceConfig.ConnectionSettings(5_000, 5_000, 2, false, null),
                new ConfluenceConfig.PaginationSettings(50, 5),
                new ConfluenceConfig.ApiPaths("/content/", "/content/search", "/space", "/child/attachment", "body.storage,version", "permissions"),
                new ConfluenceConfig.CacheSettings(300000, 5000),
                new ConfluenceConfig.PollingSettings(60000)
        );
        service = new ConfluenceAttachmentHttpDownloaderAdapter(config, objectMapper);

        // replace private final httpClient with a mock using reflection
        httpClient = mock(HttpClient.class);
        Field f = ConfluenceAttachmentHttpDownloaderAdapter.class.getDeclaredField("httpClient");
        f.setAccessible(true);
        f.set(service, httpClient);
    }

    @Test
    @DisplayName("returns empty when inputs are blank")
    void shouldReturnEmptyWhenInputsBlank() {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(service.downloadAttachmentContent(null, "file.txt").join()).isEmpty();
        softly.assertThat(service.downloadAttachmentContent(" ", "file.txt").join()).isEmpty();
        softly.assertThat(service.downloadAttachmentContent("123", null).join()).isEmpty();
        softly.assertThat(service.downloadAttachmentContent("123", " ").join()).isEmpty();
        softly.assertAll();
    }

    @Test
    @DisplayName("returns empty when listing status not 200")
    void shouldReturnEmptyWhenListStatusNot200() {
        // arrange list call -> 500
        doReturn(CompletableFuture.completedFuture(mockStringResponse(500)))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        Optional<byte[]> result = service.downloadAttachmentContent("123", "file.txt").join();
        assertThat(result).isEmpty();
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("returns empty when results missing or not array")
    void shouldReturnEmptyWhenResultsMissingOrNotArray() {
        // results missing
        doReturn(CompletableFuture.completedFuture(mockStringResponse(200)))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(service.downloadAttachmentContent("123", "file.txt").join()).isEmpty();

        // results present but not array
        doReturn(CompletableFuture.completedFuture(mockStringResponse(200)))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(service.downloadAttachmentContent("123", "file.txt").join()).isEmpty();
    }

    @Test
    @DisplayName("returns empty when attachment title not found")
    void shouldReturnEmptyWhenAttachmentNotFound() {
        doReturn(CompletableFuture.completedFuture(mockStringResponse(200)))
                .when(httpClient)
                .sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        Optional<byte[]> result = service.downloadAttachmentContent("123", "file.txt").join();
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadAttachmentContent_normalizesDownloadPathWithoutLeadingSlash() throws Exception {
        String okList = attachmentsJson(List.of(
                attachment()
        ));
        var listOk = mock(HttpResponse.class);
        when(listOk.statusCode()).thenReturn(200);
        when(listOk.body()).thenReturn(okList);
        HttpResponse<byte[]> bytesOk = mock(HttpResponse.class);
        when(bytesOk.statusCode()).thenReturn(200);
        when(bytesOk.body()).thenReturn("X".getBytes());
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(listOk))
                .thenReturn(CompletableFuture.completedFuture(bytesOk));
        Optional<byte[]> bytes = service.downloadAttachmentContent("1", "wanted.bin").get();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(bytes).isPresent();
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_BytesStatusNot200")
    void Should_ReturnEmpty_When_BytesStatusNot200() {
        String okList = attachmentsJson(List.of(attachment()));
        HttpResponse<String> listOk = mockResponse(200, okList);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> bytes404 = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(bytes404.statusCode()).thenReturn(404);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(listOk))
                .thenReturn(CompletableFuture.completedFuture(bytes404));
        Optional<byte[]> result = service.downloadAttachmentContent("1", "wanted.bin").join();
        assertThat(result).isEmpty();
        verify(httpClient, times(2)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Should_SucceedAfterRetry_When_FirstAttemptServerError")
    void Should_SucceedAfterRetry_When_FirstAttemptServerError() {
        String okList = attachmentsJson(List.of(attachment()));
        HttpResponse<String> listOk = mockResponse(200, okList);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> bytes500 = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(bytes500.statusCode()).thenReturn(500);
        HttpResponse<byte[]> bytes200 = mockResponse(200, "ok".getBytes());
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(listOk))
                .thenReturn(CompletableFuture.completedFuture(bytes500))
                .thenReturn(CompletableFuture.completedFuture(bytes200));
        Optional<byte[]> result = service.downloadAttachmentContent("1", "wanted.bin").join();
        assertThat(result).isPresent();
        verify(httpClient, times(3)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- helpers
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockStringResponse(int status) {
        HttpResponse<String> r = (HttpResponse<String>) mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        return r;
    }

    private static String attachmentsJson(List<ObjectNode> attachments) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        attachments.forEach(results::add);
        root.set("results", results);
        return root.toString();
    }

    private static ObjectNode attachment() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("title", "wanted.bin");
        ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("mediaType", "application/octet-stream");
        node.set("metadata", metadata);
        ObjectNode links = JsonNodeFactory.instance.objectNode();
        links.put("download", "d/wanted.bin");
        node.set("_links", links);
        return node;
    }
}
