package pro.softcom.sentinelle.application.pii.reporting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Unit tests for {@link PiiContextExtractor}.
 * <p>
 * Verifies extraction, masking and truncation of PII context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PiiContextExtractor - PII context extraction")
class PiiContextExtractorTest {

    private PiiContextExtractor piiContextExtractor;

    @BeforeEach
    void setUp() {
        var realParserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        piiContextExtractor = new PiiContextExtractor(realParserFactory);
    }

    @ParameterizedTest(name = "{index} -> should mask occurrence and keep line snippet for type={2}")
    @MethodSource("basicContextCases")
    @DisplayName("Should_ExtractAndMaskContext_BasicCases")
    void Should_ExtractAndMaskContext_BasicCases(String source, String occurrence, String type) {
        int start = source.indexOf(occurrence);
        int end = start + occurrence.length();
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, type);
        assertThat(ctx)
                .contains("[" + type + "]")
                .doesNotContain(occurrence);
    }

    static Stream<Arguments> basicContextCases() {
        String s1 = "My email is john.doe@example.com and phone";
        String s2 = "Call me at 06 12 34 56 78 tonight";
        return Stream.of(
                Arguments.of(s1, "john.doe@example.com", "EMAIL"),
                Arguments.of(s2, "06 12 34 56 78", "PHONE")
        );
    }

    @Test
    @DisplayName("Should_ExtractAndMaskContext_When_PiiInMiddleOfLine")
    void Should_ExtractAndMaskContext_When_PiiInMiddleOfLine() {
        // Given
        String source = "My email is john.doe@example.com and my phone";
        int start = 14;
        int end = 34;

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(ctx).isNotNull();
            softly.assertThat(ctx).contains("[EMAIL]");
            softly.assertThat(ctx).doesNotContain("john.doe@example.com");
            softly.assertThat(ctx).contains("My email is");
        });
    }

    @Test
    @DisplayName("Should_BeIdempotent_When_ContextAlreadyExists")
    void Should_BeIdempotent_When_ContextAlreadyExists() {
        // Given
        String existingContext = "Existing context [EMAIL] value";
        PiiEntity entity = PiiEntity.builder()
                .startPosition(14)
                .endPosition(34)
                .piiType("EMAIL")
                .sensitiveContext(existingContext)
                .build();

        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-1")
                .sourceContent("My email is john.doe@example.com and my phone")
                .detectedEntities(List.of(entity))
                .build();

        // When
        ScanResult result = piiContextExtractor.enrichContexts(scanResult);

        // Then
        assertThat(result.detectedEntities().getFirst().sensitiveContext())
                .isEqualTo(existingContext);
    }

    @Test
    @DisplayName("Should_ReturnNull_When_SourceContentIsNull")
    void Should_ReturnNull_When_SourceContentIsNull() {
        // When
        String ctx = piiContextExtractor.extractMaskedContext(null, 0, 10, "EMAIL");
        // Then
        assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("Should_ExtractOnlyCurrentLine_When_SourceHasMultipleLines")
    void Should_ExtractOnlyCurrentLine_When_SourceHasMultipleLines() {
        // Given
        String source = "Line 1\nMy email is john.doe@example.com here\nLine 3";
        String occurrence = "john.doe@example.com";
        int start = source.indexOf(occurrence);
        int end = start + occurrence.length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).contains("My email is");
            softly.assertThat(context).contains("here");
            softly.assertThat(context).doesNotContain("Line 1");
            softly.assertThat(context).doesNotContain("Line 3");
        });
    }

    @Test
    @DisplayName("Should_UseFallbackType_When_TypeIsNull")
    void Should_UseFallbackType_When_TypeIsNull() {
        // Given
        String source = "My email is john.doe@example.com here";
        int start = source.indexOf("john.doe@example.com");
        int end = start + "john.doe@example.com".length();

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, null);

        // Then
        assertThat(ctx).contains("[UNKNOWN]");
    }

    @Test
    @DisplayName("Should_UseFallbackType_When_TypeIsBlank")
    void Should_UseFallbackType_When_TypeIsBlank() {
        // Given
        String source = "My email is john.doe@example.com here";
        int start = source.indexOf("john.doe@example.com");
        int end = start + "john.doe@example.com".length();

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "   ");

        // Then
        assertThat(ctx).contains("[UNKNOWN]");
    }

    @Test
    @DisplayName("Should_HandleMultipleEntities_Independently")
    void Should_HandleMultipleEntities_Independently() {
        // Given
        String source = "Email: john@example.com, Phone: 0123456789";
        int emailStart = 7;
        int emailEnd = 23;
        int phoneStart = 32;
        int phoneEnd = 42;

        // When
        String emailCtx = piiContextExtractor.extractMaskedContext(source, emailStart, emailEnd, "EMAIL");
        String phoneCtx = piiContextExtractor.extractMaskedContext(source, phoneStart, phoneEnd, "PHONE");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(emailCtx).contains("[EMAIL]");
            softly.assertThat(emailCtx).doesNotContain("john@example.com");
            softly.assertThat(phoneCtx).contains("[PHONE]");
            softly.assertThat(phoneCtx).doesNotContain("0123456789");
        });
    }

    @Test
    @DisplayName("Should_HandlePiiAtStartOfLine_When_ExtractingContext")
    void Should_HandlePiiAtStartOfLine_When_ExtractingContext() {
        // Given
        String source = "john.doe@example.com is my email";
        int start = 0;
        int end = "john.doe@example.com".length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).startsWith("[EMAIL]");
            softly.assertThat(context).contains("is my email");
        });
    }

    @Test
    @DisplayName("Should_HandlePiiAtEndOfLine_When_ExtractingContext")
    void Should_HandlePiiAtEndOfLine_When_ExtractingContext() {
        // Given
        String source = "My email is john.doe@example.com";
        int start = "My email is ".length();
        int end = start + "john.doe@example.com".length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).endsWith("[EMAIL]");
            softly.assertThat(context).contains("My email is");
        });
    }

    @Test
    @DisplayName("Should_ReturnNull_When_ExtractingFromNullSource")
    void Should_ReturnNull_When_ExtractingFromNullSource() {
        // When
        String result = piiContextExtractor.extractMaskedLineContext(null, 0, 10, "EMAIL");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_ReturnNull_When_ExtractingFromBlankSource")
    void Should_ReturnNull_When_ExtractingFromBlankSource() {
        // When
        String result = piiContextExtractor.extractMaskedLineContext("   ", 0, 10, "EMAIL");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_HandleOutOfBoundsPositions_When_ExtractingContext")
    void Should_HandleOutOfBoundsPositions_When_ExtractingContext() {
        // Given
        String source = "Short text";
        int start = 0;
        int end = 1000; // out of bounds

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then
        assertThat(ctx).isNotNull();
    }

    @Test
    @DisplayName("Should_NotCutWords_When_Truncating")
    void Should_NotCutWords_When_Truncating() {
        // Given: build a long sentence where a word would be cut without word-boundary snapping
        String prefix = "It is very very very very important that nobody should ";
        String fillerLeft = "x".repeat(140);
        String pii = "john.doe@example.com";
        String fillerRight = " y".repeat(140);
        String source = fillerLeft + prefix + pii + fillerRight;
        int start = (fillerLeft + prefix).length();
        int end = start + pii.length();

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then: the beginning after the ellipsis should start at a word boundary (e.g., include 'important' fully)
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("[EMAIL]");
            softly.assertThat(ctx).doesNotContain("…ortant"); // avoid cutting 'important'
        });
    }

    @Test
    @DisplayName("Should_Mask_OtherPii_In_Context_When_MultiplePiiInLine")
    void Should_Mask_OtherPii_In_Context_When_MultiplePiiInLine() {
        // Given
        String source = "Contact: john@example.com and phone 06 11 22 33 44 are provided here";
        int emailStart = source.indexOf("john@example.com");
        int emailEnd = emailStart + "john@example.com".length();
        int phoneStart = source.indexOf("06 11 22 33 44");
        int phoneEnd = phoneStart + "06 11 22 33 44".length();
        var entities = List.of(
            PiiEntity.builder().startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            PiiEntity.builder().startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build()
        );

        // When: extract context for EMAIL but provide all entities to ensure PHONE is masked too
        String ctx = piiContextExtractor.extractMaskedContext(source, emailStart, emailEnd, "EMAIL", entities);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("[EMAIL]");
            softly.assertThat(ctx).contains("[PHONE]");
            softly.assertThat(ctx).doesNotContain("06 11 22 33 44");
        });
    }

    @Test
    @DisplayName("Should_MaskAllPiiInSameLine_When_EnrichingMultipleEntities")
    void Should_MaskAllPiiInSameLine_When_EnrichingMultipleEntities() {
        // Given: Multiple PIIs on the same line
        String source = "Contact: john@example.com and phone 06 11 22 33 44 provided";
        int emailStart = source.indexOf("john@example.com");
        int emailEnd = emailStart + "john@example.com".length();
        int phoneStart = source.indexOf("06 11 22 33 44");
        int phoneEnd = phoneStart + "06 11 22 33 44".length();
        
        PiiEntity emailEntity = PiiEntity.builder()
                .startPosition(emailStart)
                .endPosition(emailEnd)
                .piiType("EMAIL")
                .build();
        
        PiiEntity phoneEntity = PiiEntity.builder()
                .startPosition(phoneStart)
                .endPosition(phoneEnd)
                .piiType("PHONE")
                .build();

        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-1")
                .sourceContent(source)
                .detectedEntities(List.of(emailEntity, phoneEntity))
                .build();

        // When: Enriching contexts via enrichContexts (not direct extract call)
        ScanResult result = piiContextExtractor.enrichContexts(scanResult);

        // Then: BOTH entities should have contexts with BOTH PIIs masked
        assertSoftly(softly -> {
            softly.assertThat(result.detectedEntities()).hasSize(2);
            
            PiiEntity enrichedEmail = result.detectedEntities().get(0);
            PiiEntity enrichedPhone = result.detectedEntities().get(1);
            
            // Email context should mask both EMAIL and PHONE
            softly.assertThat(enrichedEmail.maskedContext()).isNotNull();
            softly.assertThat(enrichedEmail.maskedContext()).contains("[EMAIL]");
            softly.assertThat(enrichedEmail.maskedContext()).contains("[PHONE]");
            softly.assertThat(enrichedEmail.maskedContext()).doesNotContain("john@example.com");
            softly.assertThat(enrichedEmail.maskedContext()).doesNotContain("06 11 22 33 44");
            
            // Phone context should mask both EMAIL and PHONE
            softly.assertThat(enrichedPhone.maskedContext()).isNotNull();
            softly.assertThat(enrichedPhone.maskedContext()).contains("[EMAIL]");
            softly.assertThat(enrichedPhone.maskedContext()).contains("[PHONE]");
            softly.assertThat(enrichedPhone.maskedContext()).doesNotContain("john@example.com");
            softly.assertThat(enrichedPhone.maskedContext()).doesNotContain("06 11 22 33 44");
        });
    }

    // Tests for extractSensitiveContext

    @ParameterizedTest(name = "{index} -> should extract unmasked context for: {3}")
    @MethodSource("sensitiveContextExtractionCases")
    @DisplayName("Should_ExtractUnmaskedContext_When_UsingSensitiveContextExtraction")
    void Should_ExtractUnmaskedContext_When_UsingSensitiveContextExtraction(
            String source, String piiValue, String expectedContext, String testCase) {
        // Given
        int start = source.indexOf(piiValue);
        int end = start + piiValue.length();

        // When
        String ctx = piiContextExtractor.extractSensitiveContext(source, start, end);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(ctx).isNotNull();
            softly.assertThat(ctx).contains(piiValue);
            softly.assertThat(ctx).contains(expectedContext);
            softly.assertThat(ctx).doesNotContain("[EMAIL]");
            softly.assertThat(ctx).doesNotContain("[UNKNOWN]");
        });
    }

    static Stream<Arguments> sensitiveContextExtractionCases() {
        return Stream.of(
                Arguments.of("My email is john.doe@example.com and my phone", 
                            "john.doe@example.com", "My email is", "PII in middle of line"),
                Arguments.of("john.doe@example.com is my email address", 
                            "john.doe@example.com", "is my email", "PII at start of line"),
                Arguments.of("My email address is john.doe@example.com", 
                            "john.doe@example.com", "My email address is", "PII at end of line"),
                Arguments.of("Contact john.doe@example.com for info", 
                            "john.doe@example.com", "Contact", "PII with surrounding text")
        );
    }

    @ParameterizedTest(name = "{index} -> should return null for: {1}")
    @MethodSource("invalidSourceCases")
    @DisplayName("Should_ReturnNull_When_ExtractingSensitiveContextFromInvalidSource")
    void Should_ReturnNull_When_ExtractingSensitiveContextFromInvalidSource(String source, String testCase) {
        // When
        String result = piiContextExtractor.extractSensitiveContext(source, 0, 10);

        // Then
        assertThat(result).isNull();
    }

    static Stream<Arguments> invalidSourceCases() {
        return Stream.of(
                Arguments.of(null, "null source"),
                Arguments.of("", "empty source"),
                Arguments.of("   ", "blank source"),
                Arguments.of("\t\n", "whitespace only source")
        );
    }

    @ParameterizedTest(name = "{index} -> {2}")
    @MethodSource("multilineSourceCases")
    @DisplayName("Should_ExtractOnlyCurrentLine_When_SensitiveContextFromMultilineSource")
    void Should_ExtractOnlyCurrentLine_When_SensitiveContextFromMultilineSource(
            String source, String piiValue, String testCase) {
        // Given
        int start = source.indexOf(piiValue);
        int end = start + piiValue.length();

        // When
        String context = piiContextExtractor.extractSensitiveContext(source, start, end);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).contains(piiValue);
            softly.assertThat(context).doesNotContain("Line 1");
            softly.assertThat(context).doesNotContain("Line 3");
        });
    }

    static Stream<Arguments> multilineSourceCases() {
        return Stream.of(
                Arguments.of("Line 1 with content\nMy email is john.doe@example.com here\nLine 3 with more", 
                            "john.doe@example.com", "email in middle line"),
                Arguments.of("Line 1 data\nPhone: 06 11 22 33 44 end\nLine 3 more", 
                            "06 11 22 33 44", "phone in middle line"),
                Arguments.of("Line 1\nStart of line john@test.com text\nLine 3", 
                            "john@test.com", "PII after line start")
        );
    }

    @Test
    @DisplayName("Should_HandleOutOfBoundsPositions_When_ExtractingSensitiveContext")
    void Should_HandleOutOfBoundsPositions_When_ExtractingSensitiveContext() {
        // Given
        String source = "Short text with data@test.com";
        int start = 0;
        int end = 1000; // out of bounds

        // When
        String ctx = piiContextExtractor.extractSensitiveContext(source, start, end);

        // Then
        assertThat(ctx).isNotNull();
    }

    @Test
    @DisplayName("Should_PreserveAllPiiValues_When_MultiplePiiInSameLine")
    void Should_PreserveAllPiiValues_When_MultiplePiiInSameLine() {
        // Given
        String source = "Contact: john@example.com and phone 06 11 22 33 44 provided";
        int emailStart = source.indexOf("john@example.com");
        int emailEnd = emailStart + "john@example.com".length();

        // When
        String ctx = piiContextExtractor.extractSensitiveContext(source, emailStart, emailEnd);

        // Then: ALL PII values should be present (not masked) since this is sensitive context
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("john@example.com");
            softly.assertThat(ctx).contains("06 11 22 33 44");
            softly.assertThat(ctx).doesNotContain("[EMAIL]");
            softly.assertThat(ctx).doesNotContain("[PHONE]");
        });
    }

    @Test
    @DisplayName("Should_ExtractBothContexts_When_EnrichingEntities")
    void Should_ExtractBothContexts_When_EnrichingEntities() {
        // Given
        String source = "My email is john.doe@example.com and my data";
        int start = source.indexOf("john.doe@example.com");
        int end = start + "john.doe@example.com".length();
        
        PiiEntity entity = PiiEntity.builder()
                .startPosition(start)
                .endPosition(end)
                .piiType("EMAIL")
                .build();

        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-1")
                .sourceContent(source)
                .detectedEntities(List.of(entity))
                .build();

        // When
        ScanResult result = piiContextExtractor.enrichContexts(scanResult);

        // Then: Both sensitiveContext and maskedContext should be populated
        PiiEntity enriched = result.detectedEntities().getFirst();
        assertSoftly(softly -> {
            // Sensitive context contains real PII value
            softly.assertThat(enriched.sensitiveContext()).isNotNull();
            softly.assertThat(enriched.sensitiveContext()).contains("john.doe@example.com");
            softly.assertThat(enriched.sensitiveContext()).doesNotContain("[EMAIL]");
            
            // Masked context contains token instead of PII value
            softly.assertThat(enriched.maskedContext()).isNotNull();
            softly.assertThat(enriched.maskedContext()).contains("[EMAIL]");
            softly.assertThat(enriched.maskedContext()).doesNotContain("john.doe@example.com");
        });
    }

    @Test
    @DisplayName("Should_NotCutWords_When_TruncatingSensitiveContext")
    void Should_NotCutWords_When_TruncatingSensitiveContext() {
        // Given: build a long sentence where a word would be cut without word-boundary snapping
        String prefix = "Important data that nobody should see ";
        String fillerLeft = "x".repeat(140);
        String pii = "john.doe@example.com";
        String fillerRight = " y".repeat(140);
        String source = fillerLeft + prefix + pii + fillerRight;
        int start = (fillerLeft + prefix).length();
        int end = start + pii.length();

        // When
        String ctx = piiContextExtractor.extractSensitiveContext(source, start, end);

        // Then: should not cut words and preserve the PII value
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("john.doe@example.com");
            softly.assertThat(ctx).doesNotContain("…ortant"); // avoid cutting 'Important'
        });
    }

    @Test
    @DisplayName("Should_MaskEntirePiiValue_When_PositionsAreCorrect")
    void Should_MaskEntirePiiValue_When_PositionsAreCorrect() {
        // Given: a credit card number at specific positions
        String creditCard = "4916632082457636";
        String source = "Pay with card " + creditCard + " for order";
        int start = source.indexOf(creditCard);
        int end = start + creditCard.length();

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "CREDIT_CARD");

        // Then: the entire credit card should be masked, not just part of it
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("[CREDIT_CARD]");
            softly.assertThat(ctx).doesNotContain(creditCard);
            softly.assertThat(ctx).doesNotContain("4916632"); // No partial credit card number
            softly.assertThat(ctx).contains("Pay with card");
        });
    }

    @Test
    @DisplayName("Should_MaskEntirePiiValue_When_PositionsPointToExactValue")
    void Should_MaskEntirePiiValue_When_PositionsPointToExactValue() {
        // Given: exact scenario from bug report - positions 347 to 363
        String prefix = "X".repeat(347);
        String creditCard = "4916632082457636";
        String suffix = " end of text";
        String source = prefix + creditCard + suffix;
        int start = 347;
        int end = 363;

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "CREDIT_CARD");

        // Then: the entire credit card must be masked
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("[CREDIT_CARD]");
            softly.assertThat(ctx).doesNotContain(creditCard);
            softly.assertThat(ctx).doesNotContain("4916632"); // Should not have partial number
            softly.assertThat(ctx).doesNotContain("082457636"); // Should not have partial number
        });
    }

}
