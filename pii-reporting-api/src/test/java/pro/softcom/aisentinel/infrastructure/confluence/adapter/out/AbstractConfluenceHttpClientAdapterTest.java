package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractConfluenceHttpClientAdapterTest {

    @Mock
    private ConfluenceConnectionConfig config;
    @Mock
    private HttpClient httpClient;

    private ConfluenceCloudHttpClientAdapter adapter;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(config.baseUrl()).thenReturn("https://confluence.test.com");
        lenient().when(config.username()).thenReturn("testuser");
        lenient().when(config.apiToken()).thenReturn("testtoken");
        when(config.connectTimeout()).thenReturn(5000);
        lenient().when(config.readTimeout()).thenReturn(10000);
        when(config.pagesLimit()).thenReturn(50);
        adapter = new ConfluenceCloudHttpClientAdapter(config, new ObjectMapper());
        ReflectionTestUtils.setField(adapter.retryExecutor, "httpClient", httpClient);
    }

    @AfterEach
    void close() {
        adapter.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 70, 150, 175, 5100})
    void Should_RetrieveEveryPage_When_SpaceSpansMultipleBatches(int availablePages) throws Exception {
        givenSpaceContainsPages(availablePages);

        var pages = adapter.getAllPagesInSpace("TEST").get();

        assertThat(pages).extracting(ConfluencePage::id)
            .containsExactlyElementsOf(IntStream.range(0, availablePages).mapToObj(Integer::toString).toList());
        assertThat(requests).containsExactlyElementsOf(IntStream.rangeClosed(0, availablePages / 50)
            .mapToObj(batch -> "limit=50&start=" + batch * 50).toList());
    }

    @SuppressWarnings("unchecked")
    private void givenSpaceContainsPages(int availablePages) {
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String query = request.uri().getQuery();
            requests.add(query.substring(query.indexOf("limit=")));
            var parameters = Arrays.stream(query.split("&")).map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
            int start = Integer.parseInt(parameters.get("start"));
            int limit = Integer.parseInt(parameters.get("limit"));
            String pages = IntStream.range(start, Math.min(start + limit, availablePages))
                .mapToObj(id -> "{\"id\":\"%d\",\"title\":\"Page %d\"}".formatted(id, id))
                .collect(Collectors.joining(","));
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"page\":{\"results\":[" + pages + "]}}");
            return CompletableFuture.completedFuture(response);
        });
    }
}
