package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.http;

import static org.mockito.Mockito.lenient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

/**
 * Unit tests for ConfluenceApiUrlBuilder.
 * Focuses on business-oriented URL building rules for Confluence REST API endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ConfluenceApiUrlBuilderTest {

    @Mock
    private ConfluenceConnectionConfig config;

    private ConfluenceApiUrlBuilder urlBuilder;

    @BeforeEach
    void setUp() {
        // Default configuration values used by UrlBuilder
        lenient().when(config.getRestApiUrl()).thenReturn("https://confluence.test.com/rest/api");
        lenient().when(config.contentPath()).thenReturn("/content/");
        lenient().when(config.spacePath()).thenReturn("/space");
        lenient().when(config.searchContentPath()).thenReturn("/content/search");
        lenient().when(config.defaultPageExpands()).thenReturn("body.storage,version,metadata");
        lenient().when(config.defaultSpaceExpands()).thenReturn("permissions,metadata");

        urlBuilder = new ConfluenceApiUrlBuilder(config);
    }

    @Test
    void Should_BuildPageUri_When_ValidInputs() {
        var pageId = "12345";

        URI uri = urlBuilder.buildPageUri(pageId);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri).isNotNull();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/content/12345?expand=body.storage,version,metadata");
        softly.assertAll();
    }

    @Test
    void Should_BuildSpacePagesUri_When_PaginationProvided() {
        var spaceKey = "SPACE";
        int startIndex = 10;
        int pageSize = 50;

        URI uri = urlBuilder.buildSpacePagesUri(spaceKey, startIndex, pageSize);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/space/SPACE/content?expand=version,body.storage&limit=50&start=10");
        softly.assertAll();
    }

    @Test
    void Should_BuildSearchUri_When_CqlContainsSpecialCharacters() {
        var cql = "title ~ \"My Page: v1\" AND space = \"SPACE 1\"";
        var expectedEncoded = URLEncoder.encode(cql, StandardCharsets.UTF_8);

        URI uri = urlBuilder.buildSearchUri(cql);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString()).startsWith("https://confluence.test.com/rest/api/content/search?cql=");
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/content/search?cql=" + expectedEncoded + "&expand=body.storage,version");
        softly.assertAll();
    }

    @Test
    void Should_BuildSpaceUri_When_KeyProvided() {
        var spaceKey = "SPACE";

        URI uri = urlBuilder.buildSpaceUri(spaceKey);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/space/SPACE?expand=permissions,metadata");
        softly.assertAll();
    }

    @Test
    void Should_BuildUpdatePageUri_When_PageIdGiven() {
        var pageId = "999";

        URI uri = urlBuilder.buildUpdatePageUri(pageId);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/content/999");
        softly.assertAll();
    }

    @Test
    void Should_BuildAllSpacesUri_When_PaginationProvided() {
        int startIndex = 0;
        int pageSize = 25;

        URI uri = urlBuilder.buildAllSpacesUri(startIndex, pageSize);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/space?expand=permissions,metadata&limit=25&start=0");
        softly.assertAll();
    }

    @Test
    void Should_BuildConnectionTestUri_When_Called() {
        URI uri = urlBuilder.buildConnectionTestUri();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(uri.toString())
            .isEqualTo("https://confluence.test.com/rest/api/space");
        softly.assertAll();
    }

    @Test
    void Should_BuildContentSearchModifiedSinceUri_When_SpaceAndDateProvided() {
        var spaceKey = "SPACE Key"; // contains space to verify proper encoding within CQL
        var sinceDate = "2024-12-31T23:59:59Z";

        // Build using the production method
        var producedUri = urlBuilder.buildContentSearchModifiedSinceUri(spaceKey, sinceDate);

        // Build expected URI mirroring the builder's rules
        var builderCql = String.format("lastModified>=\"%s\" AND space=\"%s\"", sinceDate, spaceKey);
        var builderEncoded = URLEncoder.encode(builderCql, StandardCharsets.UTF_8);
        var expectedUri = "https://confluence.test.com/rest/api/content/search?cql=" + builderEncoded + "&expand=version,history.lastUpdated";

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(producedUri.toString()).isEqualTo(expectedUri);
        softly.assertAll();
    }
}
