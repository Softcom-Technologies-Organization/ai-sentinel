package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("PlainTextParser - Plain text content parsing")
class PlainTextParserTest {

    private static final String NEWLINE = "\n";
    private final PlainTextParser parser = new PlainTextParser();

    // ========== findLineStart Tests ==========

    @Test
    @DisplayName("Should_ReturnZero_When_PositionInFirstLine")
    void Should_ReturnZero_When_PositionInFirstLine() {
        // Given
        String source = "Hello world";
        
        // When
        int lineStart = parser.findLineStart(source, 5);
        
        // Then
        assertThat(lineStart).isZero();
    }

    @Test
    @DisplayName("Should_FindLineStart_When_PositionInSecondLine")
    void Should_FindLineStart_When_PositionInSecondLine() {
        // Given
        String line1 = "Line 1";
        String line2 = "Line 2";
        String source = line1 + NEWLINE + line2;
        int positionInLine2 = line1.length() + NEWLINE.length() + 3; // 3 chars into line2
        
        // When
        int lineStart = parser.findLineStart(source, positionInLine2);
        
        // Then
        int expectedStartOfLine2 = line1.length() + NEWLINE.length();
        assertThat(lineStart).isEqualTo(expectedStartOfLine2);
    }

    @Test
    @DisplayName("Should_FindLineStart_When_MultipleLines")
    void Should_FindLineStart_When_MultipleLines() {
        // Given
        String lineA = "A";
        String lineB = "B";
        String lineC = "C";
        String source = lineA + NEWLINE + lineB + NEWLINE + lineC;
        
        // When/Then
        assertSoftly(softly -> {
            // Line A starts at beginning
            softly.assertThat(parser.findLineStart(source, 0)).isZero();
            
            // Line B starts after first newline
            int lineBStart = lineA.length() + NEWLINE.length();
            softly.assertThat(parser.findLineStart(source, lineBStart)).isEqualTo(lineBStart);
            
            // Line C starts after second newline
            int lineCStart = lineBStart + lineB.length() + NEWLINE.length();
            softly.assertThat(parser.findLineStart(source, lineCStart)).isEqualTo(lineCStart);
        });
    }

    @Test
    @DisplayName("Should_HandleEmptyLines_When_FindingLineStart")
    void Should_HandleEmptyLines_When_FindingLineStart() {
        // Given
        String emptyLine = "";
        String line2 = "Line 2";
        String source = emptyLine + NEWLINE + line2;
        int positionInLine2 = NEWLINE.length(); // Start of line2
        
        // When
        int lineStart = parser.findLineStart(source, positionInLine2);
        
        // Then
        assertThat(lineStart).isEqualTo(NEWLINE.length());
    }

    @Test
    @DisplayName("Should_ClampPosition_When_PositionOutOfBounds")
    void Should_ClampPosition_When_PositionOutOfBounds() {
        // Given
        String source = "Hello";
        
        // When/Then
        assertSoftly(softly -> {
            softly.assertThat(parser.findLineStart(source, -5)).isZero();
            softly.assertThat(parser.findLineStart(source, 100)).isZero();
        });
    }

    // ========== findLineEnd Tests ==========

    @Test
    @DisplayName("Should_ReturnTextLength_When_SingleLine")
    void Should_ReturnTextLength_When_SingleLine() {
        // Given
        String source = "Hello world";
        
        // When
        int lineEnd = parser.findLineEnd(source, 0);
        
        // Then
        assertThat(lineEnd).isEqualTo(source.length());
    }

    @Test
    @DisplayName("Should_FindLineEnd_When_PositionInFirstLine")
    void Should_FindLineEnd_When_PositionInFirstLine() {
        // Given
        String line1 = "Line 1";
        String line2 = "Line 2";
        String source = line1 + NEWLINE + line2;
        int positionInLine1 = 3;
        
        // When
        int lineEnd = parser.findLineEnd(source, positionInLine1);
        
        // Then
        int expectedEndOfLine1 = line1.length(); // Before the newline
        assertThat(lineEnd).isEqualTo(expectedEndOfLine1);
    }

    @Test
    @DisplayName("Should_FindLineEnd_When_MultipleLines")
    void Should_FindLineEnd_When_MultipleLines() {
        // Given
        String lineA = "A";
        String lineB = "B";
        String lineC = "C";
        String source = lineA + NEWLINE + lineB + NEWLINE + lineC;
        
        // When/Then
        assertSoftly(softly -> {
            // Line A ends before first newline
            softly.assertThat(parser.findLineEnd(source, 0)).isEqualTo(lineA.length());
            
            // Line B ends before second newline
            int lineBStart = lineA.length() + NEWLINE.length();
            softly.assertThat(parser.findLineEnd(source, lineBStart))
                  .isEqualTo(lineBStart + lineB.length());
            
            // Line C ends at source end
            int lineCStart = lineBStart + lineB.length() + NEWLINE.length();
            softly.assertThat(parser.findLineEnd(source, lineCStart)).isEqualTo(source.length());
        });
    }

    @Test
    @DisplayName("Should_HandleEmptyLine_When_FindingLineEnd")
    void Should_HandleEmptyLine_When_FindingLineEnd() {
        // Given
        String emptyLine = "";
        String line2 = "Line 2";
        String source = emptyLine + NEWLINE + line2;
        int positionInEmptyLine = 0;
        
        // When
        int lineEnd = parser.findLineEnd(source, positionInEmptyLine);
        
        // Then
        assertThat(lineEnd).isZero(); // Empty line has no content
    }

    // ========== cleanText Tests ==========

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideTextPreservationCases")
    @DisplayName("Should_PreserveTextUnchanged_When_CleaningPlainText")
    void Should_PreserveTextUnchanged_When_CleaningPlainText(String description, String text) {
        // When
        String cleaned = parser.cleanText(text);
        
        // Then
        assertThat(cleaned).isEqualTo(text);
    }

    static Stream<Arguments> provideTextPreservationCases() {
        return Stream.of(
            Arguments.of("plain text", "Hello world"),
            Arguments.of("whitespace and tabs", "  spaces  \n\ttabs  "),
            Arguments.of("HTML tags", "<html>content</html>")
        );
    }

    @Test
    @DisplayName("Should_ReturnNull_When_InputIsNull")
    void Should_ReturnNull_When_InputIsNull() {
        // When
        String cleaned = parser.cleanText(null);
        
        // Then
        assertThat(cleaned).isNull();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_InputIsEmpty")
    void Should_ReturnEmpty_When_InputIsEmpty() {
        // When
        String cleaned = parser.cleanText("");
        
        // Then
        assertThat(cleaned).isEmpty();
    }

    // ========== getContentType Test ==========

    @Test
    @DisplayName("Should_ReturnPlainTextType_When_GetContentType")
    void Should_ReturnPlainTextType_When_GetContentType() {
        // When
        ContentType type = parser.getContentType();
        
        // Then
        assertThat(type).isEqualTo(ContentType.PLAIN_TEXT);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should_HandleConsecutiveNewlines_When_FindingBoundaries")
    void Should_HandleConsecutiveNewlines_When_FindingBoundaries() {
        // Given
        String line1 = "Line1";
        String emptyLine = "";
        String line3 = "Line3";
        String source = line1 + NEWLINE + emptyLine + NEWLINE + line3;
        // Source structure: "Line1\n\nLine3"
        
        // Line3 starts after two newlines
        int line3StartPosition = line1.length() + NEWLINE.length() + NEWLINE.length();
        
        // When
        int lineStart = parser.findLineStart(source, line3StartPosition);
        int lineEnd = parser.findLineEnd(source, line3StartPosition);
        
        // Then
        assertSoftly(softly -> {
            softly.assertThat(lineStart).isEqualTo(line3StartPosition);
            softly.assertThat(lineEnd).isEqualTo(source.length());
        });
    }
}
