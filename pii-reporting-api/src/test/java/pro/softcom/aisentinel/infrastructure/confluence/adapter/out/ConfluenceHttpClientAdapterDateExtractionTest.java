package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

class ConfluenceHttpClientAdapterDateExtractionTest {

    private ObjectMapper objectMapper;
    private ConfluenceHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Create minimal config mock
        var config = mock(ConfluenceConnectionConfig.class);
        when(config.username()).thenReturn("test");
        when(config.apiToken()).thenReturn("token");
        when(config.connectTimeout()).thenReturn(5000);
        when(config.readTimeout()).thenReturn(10000);
        when(config.maxRetries()).thenReturn(3);
        when(config.pagesLimit()).thenReturn(50);
        when(config.maxPages()).thenReturn(100);
        when(config.baseUrl()).thenReturn("https://test.com");
        when(config.getRestApiUrl()).thenReturn("https://test.com/rest/api");
        when(config.contentPath()).thenReturn("/content/");
        when(config.searchContentPath()).thenReturn("/content/search");
        when(config.spacePath()).thenReturn("/space");
        when(config.attachmentChildSuffix()).thenReturn("/child/attachment");
        when(config.defaultPageExpands()).thenReturn("body.storage");
        when(config.defaultSpaceExpands()).thenReturn("permissions");
        
        adapter = new ConfluenceHttpClientAdapter(config, objectMapper);
    }

    // ===== Tests pour tryExtractFromHistoryWhen =====

    @Test
    void Should_ExtractInstant_When_HistoryLastUpdatedWhenExists() throws Exception {
        // Arrange
        String validDate = "2025-01-10T10:30:45Z";
        JsonNode page = createPageWithHistory(validDate);

        // Act
        Optional<Instant> result = invokeTryExtractFromHistoryWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isPresent();
        softly.assertThat(result.get()).isEqualTo(Instant.parse(validDate));
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_PageHasNoHistory() throws Exception {
        // Arrange
        JsonNode page = objectMapper.createObjectNode();

        // Act
        Optional<Instant> result = invokeTryExtractFromHistoryWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_HistoryHasNoLastUpdated() throws Exception {
        // Arrange
        ObjectNode page = objectMapper.createObjectNode();
        page.set("history", objectMapper.createObjectNode());

        // Act
        Optional<Instant> result = invokeTryExtractFromHistoryWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_LastUpdatedHasNoWhen() throws Exception {
        // Arrange
        ObjectNode page = objectMapper.createObjectNode();
        ObjectNode history = objectMapper.createObjectNode();
        history.set("lastUpdated", objectMapper.createObjectNode());
        page.set("history", history);

        // Act
        Optional<Instant> result = invokeTryExtractFromHistoryWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_WhenFieldHasInvalidDate() throws Exception {
        // Arrange
        JsonNode page = createPageWithHistory("invalid-date-format");

        // Act
        Optional<Instant> result = invokeTryExtractFromHistoryWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    // ===== Tests pour tryExtractFromVersionWhen =====

    @Test
    void Should_ExtractInstant_When_VersionWhenExists() throws Exception {
        // Arrange
        String validDate = "2025-01-10T10:30:45Z";
        JsonNode page = createPageWithVersion(validDate);

        // Act
        Optional<Instant> result = invokeTryExtractFromVersionWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isPresent();
        softly.assertThat(result.get()).isEqualTo(Instant.parse(validDate));
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_PageHasNoVersion() throws Exception {
        // Arrange
        JsonNode page = objectMapper.createObjectNode();

        // Act
        Optional<Instant> result = invokeTryExtractFromVersionWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_VersionHasNoWhen() throws Exception {
        // Arrange
        ObjectNode page = objectMapper.createObjectNode();
        page.set("version", objectMapper.createObjectNode());

        // Act
        Optional<Instant> result = invokeTryExtractFromVersionWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmpty_When_VersionWhenHasInvalidDate() throws Exception {
        // Arrange
        JsonNode page = createPageWithVersion("not-a-valid-date");

        // Act
        Optional<Instant> result = invokeTryExtractFromVersionWhen(page);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEmpty();
        softly.assertAll();
    }

    // ===== Helper methods =====

    private JsonNode createPageWithHistory(String whenValue) {
        ObjectNode page = objectMapper.createObjectNode();
        ObjectNode history = objectMapper.createObjectNode();
        ObjectNode lastUpdated = objectMapper.createObjectNode();
        lastUpdated.put("when", whenValue);
        history.set("lastUpdated", lastUpdated);
        page.set("history", history);
        return page;
    }

    private JsonNode createPageWithVersion(String whenValue) {
        ObjectNode page = objectMapper.createObjectNode();
        ObjectNode version = objectMapper.createObjectNode();
        version.put("when", whenValue);
        page.set("version", version);
        return page;
    }

    @SuppressWarnings("unchecked")
    private Optional<Instant> invokeTryExtractFromHistoryWhen(JsonNode page) throws Exception {
        Method method = ConfluenceHttpClientAdapter.class.getDeclaredMethod("tryExtractFromHistoryWhen", JsonNode.class);
        method.setAccessible(true);
        return (Optional<Instant>) method.invoke(adapter, page);
    }

    @SuppressWarnings("unchecked")
    private Optional<Instant> invokeTryExtractFromVersionWhen(JsonNode page) throws Exception {
        Method method = ConfluenceHttpClientAdapter.class.getDeclaredMethod("tryExtractFromVersionWhen", JsonNode.class);
        method.setAccessible(true);
        return (Optional<Instant>) method.invoke(adapter, page);
    }
}
