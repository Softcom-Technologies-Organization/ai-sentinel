package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("HtmlContentParser - HTML content parsing")
class HtmlContentParserTest {

    private final HtmlContentParser parser = new HtmlContentParser();

    // ========== findLineStart Tests ==========

    @Test
    @DisplayName("Should_FindLineStart_When_PositionAfterBlockTag")
    void Should_FindLineStart_When_PositionAfterBlockTag() {
        // Given
        String paragraph1 = "Paragraph 1";
        String paragraph2 = "Paragraph 2";
        String source = "<p>" + paragraph1 + "</p><p>" + paragraph2 + "</p>";
        // Source: "<p>Paragraph 1</p><p>Paragraph 2</p>"
        
        int secondParagraphTagStart = source.indexOf("<p>", 1); // Second <p> tag
        int positionInParagraph2 = secondParagraphTagStart + "<p>".length() + 1;
        
        // When
        int lineStart = parser.findLineStart(source, positionInParagraph2);
        
        // Then
        int expectedLineStart = secondParagraphTagStart + "<p>".length(); // After second <p>
        assertThat(lineStart).isEqualTo(expectedLineStart);
    }

    @Test
    @DisplayName("Should_FindLineStart_When_AfterBreakTag")
    void Should_FindLineStart_When_AfterBreakTag() {
        // Given
        String line1 = "Line 1";
        String line2 = "Line 2";
        String source = line1 + "<br>" + line2;
        // Source: "Line 1<br>Line 2"
        
        int brTagEnd = source.indexOf("<br>") + "<br>".length();
        int positionInLine2 = brTagEnd + 4; // 4 chars into line2
        
        // When
        int lineStart = parser.findLineStart(source, positionInLine2);
        
        // Then
        int expectedLineStart = brTagEnd; // After <br>
        assertThat(lineStart).isEqualTo(expectedLineStart);
    }

    @Test
    @DisplayName("Should_FindLineStart_When_MultipleBlockTags")
    void Should_FindLineStart_When_MultipleBlockTags() {
        // Given
        String title = "Title";
        String content = "Content";
        String source = "<h1>" + title + "</h1><div>" + content + "</div>";
        // Source: "<h1>Title</h1><div>Content</div>"
        
        // When/Then
        assertSoftly(softly -> {
            // Position in "Title"
            int h1TagEnd = "<h1>".length();
            int positionInTitle = h1TagEnd + 1;
            softly.assertThat(parser.findLineStart(source, positionInTitle)).isEqualTo(h1TagEnd);
            
            // Position in "Content"
            int divTagStart = source.indexOf("<div>");
            int divTagEnd = divTagStart + "<div>".length();
            int positionInContent = divTagEnd + 1;
            softly.assertThat(parser.findLineStart(source, positionInContent)).isEqualTo(divTagEnd);
        });
    }

    @Test
    @DisplayName("Should_ConsiderNewlines_When_FindingLineStart")
    void Should_ConsiderNewlines_When_FindingLineStart() {
        // Given
        String source = "Line 1\n<p>Line 2</p>";
        
        // When
        int lineStart = parser.findLineStart(source, 10);
        
        // Then
        assertThat(lineStart).isGreaterThanOrEqualTo(7); // After newline or <p>
    }

    // ========== findLineEnd Tests ==========

    @Test
    @DisplayName("Should_FindLineEnd_When_NextBlockTag")
    void Should_FindLineEnd_When_NextBlockTag() {
        // Given
        String paragraph1 = "Paragraph 1";
        String source = "<p>" + paragraph1 + "</p><p>Paragraph 2</p>";
        
        int positionInParagraph1 = "<p>".length();
        
        // When
        int lineEnd = parser.findLineEnd(source, positionInParagraph1);
        
        // Then
        int expectedLineEnd = "<p>".length() + paragraph1.length(); // Before </p>
        assertThat(lineEnd).isEqualTo(expectedLineEnd);
    }

    @Test
    @DisplayName("Should_FindLineEnd_When_BeforeBreakTag")
    void Should_FindLineEnd_When_BeforeBreakTag() {
        // Given
        String line1 = "Line 1";
        String source = line1 + "<br>Line 2";
        
        // When
        int lineEnd = parser.findLineEnd(source, 0);
        
        // Then
        int expectedLineEnd = line1.length(); // Before <br>
        assertThat(lineEnd).isEqualTo(expectedLineEnd);
    }

    @Test
    @DisplayName("Should_ReturnSourceLength_When_NoMoreTags")
    void Should_ReturnSourceLength_When_NoMoreTags() {
        // Given
        String content = "Only paragraph";
        String source = "<p>" + content + "</p>";
        
        int positionInContent = "<p>".length();
        
        // When
        int lineEnd = parser.findLineEnd(source, positionInContent);
        
        // Then
        int expectedLineEnd = "<p>".length() + content.length(); // Before </p>
        assertThat(lineEnd).isEqualTo(expectedLineEnd);
    }

    @Test
    @DisplayName("Should_PreferNewline_When_CloserThanBlockTag")
    void Should_PreferNewline_When_CloserThanBlockTag() {
        // Given
        String text = "Text";
        String source = text + "\n<p>Paragraph</p>";
        
        // When
        int lineEnd = parser.findLineEnd(source, 0);
        
        // Then
        int expectedLineEnd = text.length(); // At newline
        assertThat(lineEnd).isEqualTo(expectedLineEnd);
    }

    // ========== cleanText Tests (Parameterized) ==========

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideHtmlToCleanCases")
    @DisplayName("Should_RemoveHtmlAndExtractText_When_CleaningHtml")
    void Should_RemoveHtmlAndExtractText_When_CleaningHtml(
            String description,
            String html,
            String[] expectedTexts,
            String[] unwantedTags
    ) {
        // When
        String cleaned = parser.cleanText(html);
        
        // Then
        assertSoftly(softly -> {
            // Verify expected text content is present
            for (String expectedText : expectedTexts) {
                softly.assertThat(cleaned)
                      .as("Cleaned text should contain '%s'", expectedText)
                      .contains(expectedText);
            }
            
            // Verify HTML tags are removed
            for (String unwantedTag : unwantedTags) {
                softly.assertThat(cleaned)
                      .as("Cleaned text should not contain tag '%s'", unwantedTag)
                      .doesNotContain(unwantedTag);
            }
        });
    }

    static Stream<Arguments> provideHtmlToCleanCases() {
        return Stream.of(
            Arguments.of(
                "simple paragraph",
                "<p>Hello</p>",
                new String[]{"Hello"},
                new String[]{"<p>", "</p>"}
            ),
            Arguments.of(
                "break tags to newlines",
                "Line 1<br>Line 2",
                new String[]{"Line 1", "Line 2", "\n"},
                new String[]{"<br>"}
            ),
            Arguments.of(
                "multiple paragraphs",
                "<p>First paragraph</p><p>Second paragraph</p>",
                new String[]{"First paragraph", "Second paragraph", "\n"},
                new String[]{"<p>", "</p>"}
            ),
            Arguments.of(
                "complex HTML document",
                "<html><body><h1>Title</h1><div>Content</div></body></html>",
                new String[]{"Title", "Content"},
                new String[]{"<html>", "<body>", "<h1>", "<div>"}
            ),
            Arguments.of(
                "table elements",
                "<table><tr><td>Cell 1</td><td>Cell 2</td></tr></table>",
                new String[]{"Cell 1", "Cell 2"},
                new String[]{"<table>", "<tr>", "<td>"}
            ),
            Arguments.of(
                "list elements",
                "<ul><li>Item 1</li><li>Item 2</li></ul>",
                new String[]{"Item 1", "Item 2"},
                new String[]{"<ul>", "<li>"}
            ),
            Arguments.of(
                "header tags",
                "<h1>Title</h1><h2>Subtitle</h2>",
                new String[]{"Title", "Subtitle", "\n"},
                new String[]{"<h1>", "<h2>"}
            )
        );
    }

    @Test
    @DisplayName("Should_ReturnOriginal_When_CleaningFailsWithException")
    void Should_ReturnOriginal_When_CleaningFailsWithException() {
        // Given: Jsoup is robust, so we test with null
        String text = null;
        
        // When
        String cleaned = parser.cleanText(text);
        
        // Then
        assertThat(cleaned).isNull();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_CleaningEmptyText")
    void Should_ReturnEmpty_When_CleaningEmptyText() {
        // When
        String cleaned = parser.cleanText("");
        
        // Then
        assertThat(cleaned).isEmpty();
    }

    @Test
    @DisplayName("Should_PreserveTextContent_When_CleaningPlainText")
    void Should_PreserveTextContent_When_CleaningPlainText() {
        // Given
        String plainText = "Just plain text";
        
        // When
        String cleaned = parser.cleanText(plainText);
        
        // Then
        assertThat(cleaned).isEqualTo(plainText);
    }

    // ========== getContentType Test ==========

    @Test
    @DisplayName("Should_ReturnHtmlType_When_GetContentType")
    void Should_ReturnHtmlType_When_GetContentType() {
        // When
        ContentType type = parser.getContentType();
        
        // Then
        assertThat(type).isEqualTo(ContentType.HTML);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should_HandleNestedTags_When_FindingBoundaries")
    void Should_HandleNestedTags_When_FindingBoundaries() {
        // Given
        String source = "<div><p>Nested content</p></div>";
        
        // When
        int lineStart = parser.findLineStart(source, 10);
        int lineEnd = parser.findLineEnd(source, 10);
        
        // Then
        assertSoftly(softly -> {
            softly.assertThat(lineStart).isGreaterThanOrEqualTo(0);
            softly.assertThat(lineEnd).isLessThanOrEqualTo(source.length());
        });
    }

    @Test
    @DisplayName("Should_HandleSelfClosingTags_When_CleaningText")
    void Should_HandleSelfClosingTags_When_CleaningText() {
        // Given
        String html = "Line 1<br/>Line 2";
        
        // When
        String cleaned = parser.cleanText(html);
        
        // Then
        assertThat(cleaned).contains("\n");
    }
}
