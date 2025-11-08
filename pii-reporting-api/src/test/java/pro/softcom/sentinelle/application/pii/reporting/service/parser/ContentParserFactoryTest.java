package pro.softcom.sentinelle.application.pii.reporting.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("ContentParserFactory - Parser selection")
class ContentParserFactoryTest {

    private ContentParserFactory factory;

    @BeforeEach
    void setUp() {
        var plainTextParser = new PlainTextParser();
        var htmlContentParser = new HtmlContentParser();
        factory = new ContentParserFactory(plainTextParser, htmlContentParser);
    }

    // ========== HTML Detection Tests (Parameterized) ==========

    @ParameterizedTest(name = "[{index}] {0} should be detected as HTML")
    @MethodSource("provideHtmlSources")
    @DisplayName("Should_ReturnHtmlParser_When_SourceContainsHtmlBlockTags")
    void Should_ReturnHtmlParser_When_SourceContainsHtmlBlockTags(String description, String htmlSource) {
        // When
        ContentParser parser = factory.getParser(htmlSource);
        
        // Then
        assertThat(parser)
            .as("Source with %s should return HtmlContentParser", description)
            .isInstanceOf(HtmlContentParser.class);
        assertThat(parser.getContentType()).isEqualTo(ContentType.HTML);
    }

    static Stream<Arguments> provideHtmlSources() {
        return Stream.of(
            Arguments.of("paragraph tag", "<p>This is a paragraph</p>"),
            Arguments.of("div tag", "<div>Content in div</div>"),
            Arguments.of("break tag", "Line 1<br>Line 2"),
            Arguments.of("complete document", "<html><body>Content</body></html>"),
            Arguments.of("header tags", "<h1>Title</h1><h2>Subtitle</h2>"),
            Arguments.of("list items", "<li>Item 1</li><li>Item 2</li>"),
            Arguments.of("table", "<table><tr><td>Cell</td></tr></table>")
        );
    }

    // ========== Plain Text Detection Tests (Parameterized) ==========

    @ParameterizedTest(name = "[{index}] {0} should be detected as plain text")
    @MethodSource("providePlainTextSources")
    @DisplayName("Should_ReturnPlainTextParser_When_SourceIsNotHtml")
    void Should_ReturnPlainTextParser_When_SourceIsNotHtml(String description, String plainSource) {
        // When
        ContentParser parser = factory.getParser(plainSource);
        
        // Then
        assertThat(parser)
            .as("Source with %s should return PlainTextParser", description)
            .isInstanceOf(PlainTextParser.class);
    }

    static Stream<Arguments> providePlainTextSources() {
        return Stream.of(
            Arguments.of("pure text", "This is just plain text without any HTML"),
            Arguments.of("angle brackets without HTML", "Price is < 100 and > 50"),
            Arguments.of("null source", null),
            Arguments.of("empty source", ""),
            Arguments.of("too short HTML", "<p>Hi</p>"), // Less than 10 chars
            Arguments.of("inline tags only", "Text with <span>inline</span> and <strong>tags</strong>")
        );
    }

    // ========== ContentType-based Selection Tests (Parameterized) ==========

    @ParameterizedTest
    @CsvSource({
        "HTML, HtmlContentParser",
        "PLAIN_TEXT, PlainTextParser",
        "AUTO, PlainTextParser"
    })
    @DisplayName("Should_ReturnCorrectParser_When_GivenContentType")
    void Should_ReturnCorrectParser_When_GivenContentType(ContentType type, String expectedParserClass) {
        // When
        ContentParser parser = factory.getParser(type);
        
        // Then
        assertThat(parser.getClass().getSimpleName())
            .as("ContentType.%s should return %s", type, expectedParserClass)
            .isEqualTo(expectedParserClass);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should_CheckFirst1000Characters_When_SourceIsLong")
    void Should_CheckFirst1000Characters_When_SourceIsLong() {
        // Given: HTML tag within first 1000 chars
        String longText = "Plain text ".repeat(80) + "<p>HTML here</p>";
        
        // When
        ContentParser parser = factory.getParser(longText);
        
        // Then
        assertThat(parser).isInstanceOf(HtmlContentParser.class);
    }

    @Test
    @DisplayName("Should_HandleMixedContent_When_HtmlAndPlainText")
    void Should_HandleMixedContent_When_HtmlAndPlainText() {
        // Given
        String mixedContent = "Some text before <p>HTML content</p> and after";
        
        // When
        ContentParser parser = factory.getParser(mixedContent);
        
        // Then
        assertThat(parser).isInstanceOf(HtmlContentParser.class);
    }

    @Test
    @DisplayName("Should_BeConsistent_When_CalledMultipleTimes")
    void Should_BeConsistent_When_CalledMultipleTimes() {
        // Given
        String source = "<p>HTML content</p>";
        
        // When
        ContentParser parser1 = factory.getParser(source);
        ContentParser parser2 = factory.getParser(source);
        
        // Then
        assertSoftly(softly -> {
            softly.assertThat(parser1).isInstanceOf(HtmlContentParser.class);
            softly.assertThat(parser2).isInstanceOf(HtmlContentParser.class);
        });
    }

    @Test
    @DisplayName("Should_HandleInlineHtmlTags_When_NotBlockLevel")
    void Should_HandleInlineHtmlTags_When_NotBlockLevel() {
        // Given: Only inline tags like <span>, <strong>, etc.
        String inlineHtml = "Text with <span>inline</span> and <strong>tags</strong>";
        
        // When
        ContentParser parser = factory.getParser(inlineHtml);
        
        // Then: Should return plain text parser (no block-level tags)
        assertThat(parser).isInstanceOf(PlainTextParser.class);
    }
}
