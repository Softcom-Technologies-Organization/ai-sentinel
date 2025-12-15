package pro.softcom.aisentinel.application.pii.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

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
        DetectedPersonallyIdentifiableInformation entity = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(14)
                .endPosition(34)
                .piiType("EMAIL")
                .sensitiveContext(existingContext)
                .build();

        ConfluenceContentScanResult confluenceContentScanResult = ConfluenceContentScanResult.builder()
                .scanId("scan-1")
                .sourceContent("My email is john.doe@example.com and my phone")
                .detectedPIIList(List.of(entity))
                .build();

        // When
        ConfluenceContentScanResult result = piiContextExtractor.enrichContexts(
            confluenceContentScanResult);

        // Then
        assertThat(result.detectedPIIList().getFirst().sensitiveContext())
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
    @DisplayName("Should_RemoveTrailingSensitiveSuffix_When_TokenFollowedByValueFragment")
    void Should_RemoveTrailingSensitiveSuffix_When_TokenFollowedByValueFragment() {
        // Given
        String source = "- **Numéro de compte bancaire 123456789";
        int start = source.indexOf("123456789");
        int end = start + "123456789".length();

        // When
        String ctx = piiContextExtractor.extractMaskedContext(source, start, end, "BANK_ACCOUNT");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(ctx).contains("[BANK_ACCOUNT]");
            softly.assertThat(ctx).doesNotContain("123456");
            softly.assertThat(ctx).doesNotContain("456789");
        });
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
            DetectedPersonallyIdentifiableInformation.builder().startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            DetectedPersonallyIdentifiableInformation.builder().startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build()
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
        
        DetectedPersonallyIdentifiableInformation emailEntity = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart)
                .endPosition(emailEnd)
                .piiType("EMAIL")
                .build();
        
        DetectedPersonallyIdentifiableInformation phoneEntity = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart)
                .endPosition(phoneEnd)
                .piiType("PHONE")
                .build();

        ConfluenceContentScanResult confluenceContentScanResult = ConfluenceContentScanResult.builder()
                .scanId("scan-1")
                .sourceContent(source)
                .detectedPIIList(List.of(emailEntity, phoneEntity))
                .build();

        // When: Enriching contexts via enrichContexts (not direct extract call)
        ConfluenceContentScanResult result = piiContextExtractor.enrichContexts(
            confluenceContentScanResult);

        // Then: BOTH entities should have contexts with BOTH PIIs masked
        assertSoftly(softly -> {
            softly.assertThat(result.detectedPIIList()).hasSize(2);
            
            DetectedPersonallyIdentifiableInformation enrichedEmail = result.detectedPIIList().get(0);
            DetectedPersonallyIdentifiableInformation enrichedPhone = result.detectedPIIList().get(1);
            
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
            String source, String piiValue, String expectedContext) {
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
    void Should_ReturnNull_When_ExtractingSensitiveContextFromInvalidSource(String source) {
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
            String source, String piiValue) {
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
        
        DetectedPersonallyIdentifiableInformation entity = DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(start)
                .endPosition(end)
                .piiType("EMAIL")
                .build();

        ConfluenceContentScanResult confluenceContentScanResult = ConfluenceContentScanResult.builder()
                .scanId("scan-1")
                .sourceContent(source)
                .detectedPIIList(List.of(entity))
                .build();

        // When
        ConfluenceContentScanResult result = piiContextExtractor.enrichContexts(
            confluenceContentScanResult);

        // Then: Both sensitiveContext and maskedContext should be populated
        DetectedPersonallyIdentifiableInformation enriched = result.detectedPIIList().getFirst();
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

    @Test
    @DisplayName("Should_ExtractLimitedContext_When_NoLineDelimitersExist")
    void Should_ExtractLimitedContext_When_NoLineDelimitersExist() {
        // Given: Long text without any \n or HTML tags - simulates Confluence content without delimiters
        // Bug: Without delimiters, the entire content becomes the "line" context
        String leftPadding = "A".repeat(200);
        String pii = "john.doe@example.com";
        String rightPadding = "B".repeat(200);
        String longText = leftPadding + pii + rightPadding;
        int start = 200;
        int end = 220;
        
        // When
        String context = piiContextExtractor.extractMaskedContext(longText, start, end, "EMAIL");
        
        // Then: Context should be LIMITED to ~300 chars, NOT the entire 420 chars
        assertSoftly(softly -> {
            softly.assertThat(context.length())
                .as("Context should be limited, not entire content")
                .isLessThan(350);
            softly.assertThat(context).contains("[EMAIL]");
            // Should not contain all the padding characters
            softly.assertThat(context).doesNotContain("A".repeat(180));
            softly.assertThat(context).doesNotContain("B".repeat(180));
        });
    }

    @Test
    @DisplayName("Should_ExtractLimitedSensitiveContext_When_NoLineDelimitersExist")
    void Should_ExtractLimitedSensitiveContext_When_NoLineDelimitersExist() {
        // Given: Long text without any \n or HTML tags
        String leftPadding = "A".repeat(200);
        String pii = "john.doe@example.com";
        String rightPadding = "B".repeat(200);
        String longText = leftPadding + pii + rightPadding;
        int start = 200;
        int end = 220;
        
        // When
        String context = piiContextExtractor.extractSensitiveContext(longText, start, end);
        
        // Then: Context should be LIMITED, containing the PII value
        assertSoftly(softly -> {
            softly.assertThat(context.length())
                .as("Sensitive context should be limited, not entire content")
                .isLessThan(350);
            softly.assertThat(context).contains(pii);
        });
    }

    @Test
    @DisplayName("Should_ExtractLimitedContext_When_HtmlWithoutBlockTags")
    void Should_ExtractLimitedContext_When_HtmlWithoutBlockTags() {
        // Given: HTML content with only inline tags (no block-level tags that create line breaks)
        String leftPadding = "<span>X</span>".repeat(30);
        String pii = "<strong>john.doe@example.com</strong>";
        String rightPadding = "<em>Y</em>".repeat(30);
        String htmlContent = leftPadding + pii + rightPadding;
        int start = htmlContent.indexOf("john.doe@example.com");
        int end = start + "john.doe@example.com".length();
        
        // When
        String context = piiContextExtractor.extractMaskedContext(htmlContent, start, end, "EMAIL");
        
        // Then: Context should be limited even without block tags
        assertSoftly(softly -> {
            softly.assertThat(context.length())
                .as("HTML context without block tags should be limited")
                .isLessThan(400);
            softly.assertThat(context).contains("[EMAIL]");
        });
    }

    // =============================================================================
    // BUG FIX TESTS: Position offset causing trailing sensitive characters
    // =============================================================================
    // These tests verify the fix for the bug where 2-3 trailing characters
    // remained visible after the masking token due to incorrect Math.clamp
    // minimum value in collectRelevantEntities() method

    @Test
    @DisplayName("Should_MaskEntireValue_When_NoTrailingSensitiveCharacters")
    void Should_MaskEntireValue_When_NoTrailingSensitiveCharacters() {
        // Given: Real bug scenario - Email with trailing characters visible
        String source = "Contact: john.doe@example.fr and more text";
        String email = "john.doe@example.fr";
        int start = source.indexOf(email);
        int end = start + email.length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL");

        // Then: NO trailing characters should remain visible after token
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the token")
                .contains("[EMAIL]");
            softly.assertThat(context)
                .as("Context should NOT have trailing characters like [EMAIL]fr")
                .doesNotContain("[EMAIL]fr")
                .doesNotContain("[EMAIL]f")
                .doesNotContain("[EMAIL].fr");
            softly.assertThat(context)
                .as("Context should NOT contain the actual email")
                .doesNotContain(email);
        });
    }

    @Test
    @DisplayName("Should_MaskEntireCreditCard_When_NoTrailingDigits")
    void Should_MaskEntireCreditCard_When_NoTrailingDigits() {
        // Given: Credit card with trailing digits visible (bug report)
        String creditCard = "4916632082457636";
        String source = "Pay with card " + creditCard + " for order";
        int start = source.indexOf(creditCard);
        int end = start + creditCard.length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "CREDIT_CARD");

        // Then: NO trailing digits should remain visible
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the token")
                .contains("[CREDIT_CARD]");
            softly.assertThat(context)
                .as("Context should NOT have trailing digits like [CREDIT_CARD]11 or [CREDIT_CARD]36")
                .doesNotContain("[CREDIT_CARD]11")
                .doesNotContain("[CREDIT_CARD]36")
                .doesNotContain("[CREDIT_CARD]6");
            softly.assertThat(context)
                .as("Context should NOT contain any part of the credit card")
                .doesNotContain("4916632")
                .doesNotContain("7636")
                .doesNotContain(creditCard);
        });
    }

    @Test
    @DisplayName("Should_MaskEntireBankAccount_When_NoTrailingDigits")
    void Should_MaskEntireBankAccount_When_NoTrailingDigits() {
        // Given: Bank account with trailing digits visible (bug report)
        String bankAccount = "12345678901234589";
        String source = "Account number: " + bankAccount + " is valid";
        int start = source.indexOf(bankAccount);
        int end = start + bankAccount.length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "BANK_ACCOUNT");

        // Then: NO trailing digits should remain visible
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the token")
                .contains("[BANK_ACCOUNT]");
            softly.assertThat(context)
                .as("Context should NOT have trailing digits like [BANK_ACCOUNT]89")
                .doesNotContain("[BANK_ACCOUNT]89")
                .doesNotContain("[BANK_ACCOUNT]9");
            softly.assertThat(context)
                .as("Context should NOT contain any part of the account number")
                .doesNotContain("12345678")
                .doesNotContain("4589")
                .doesNotContain(bankAccount);
        });
    }

    @Test
    @DisplayName("Should_MaskEntireSSN_When_NoTrailingDigits")
    void Should_MaskEntireSSN_When_NoTrailingDigits() {
        // Given: SSN with trailing digits visible (bug report)
        String ssn = "123-45-6789";
        String source = "SSN: " + ssn + " on file";
        int start = source.indexOf(ssn);
        int end = start + ssn.length();

        // When
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "SSN");

        // Then: NO trailing digits should remain visible
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the token")
                .contains("[SSN]");
            softly.assertThat(context)
                .as("Context should NOT have trailing digits like [SSN]19 or [SSN]89")
                .doesNotContain("[SSN]19")
                .doesNotContain("[SSN]89")
                .doesNotContain("[SSN]9");
            softly.assertThat(context)
                .as("Context should NOT contain any part of the SSN")
                .doesNotContain("6789")
                .doesNotContain(ssn);
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_MultipleEntitiesWithPositionEdgeCases")
    void Should_MaskCorrectly_When_MultipleEntitiesWithPositionEdgeCases() {
        // Given: Multiple PIIs on same line to test position calculation edge cases
        String email = "user@test.fr";
        String phone = "0612345678";
        String source = "Contact " + email + " or " + phone + " today";
        int emailStart = source.indexOf(email);
        int emailEnd = emailStart + email.length();
        int phoneStart = source.indexOf(phone);
        int phoneEnd = phoneStart + phone.length();

        var entities = List.of(
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build()
        );

        // When
        String context = piiContextExtractor.extractMaskedContext(source, emailStart, emailEnd, "EMAIL", entities);

        // Then: Both should be fully masked with NO trailing characters
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context)
                .as("Should NOT have trailing chars from email")
                .doesNotContain("[EMAIL]fr")
                .doesNotContain("[EMAIL].fr");
            softly.assertThat(context)
                .as("Should NOT have trailing chars from phone")
                .doesNotContain("[PHONE]78")
                .doesNotContain("[PHONE]8");
            softly.assertThat(context)
                .as("Should NOT leak actual values")
                .doesNotContain(email)
                .doesNotContain(phone);
        });
    }

    // =============================================================================
    // BUG FIX TESTS: Position mismatch between cleaned text and raw HTML
    // =============================================================================
    // These tests verify the fix for the bug where positions from the detector
    // (calculated on cleaned text) were applied to raw HTML, causing incoherent
    // context with HTML fragments like "ac:breakout-width="760" ac:local-id="

    @Test
    @DisplayName("Should_ExtractCoherentContext_When_PositionsFromCleanedTextAppliedToRawHtml")
    void Should_ExtractCoherentContext_When_PositionsFromCleanedTextAppliedToRawHtml() {
        // Given: Raw HTML content as stored in sourceContent
        String rawHtml = "<p>Contact us at <strong>john.doe@example.com</strong> for more info.</p>";
        
        // Simulate what the detector does: positions are on CLEANED text
        // Cleaned text: "Contact us at john.doe@example.com for more info."
        // Position of email in CLEANED text: 14 to 34
        String cleanedText = "Contact us at john.doe@example.com for more info.";
        int startInCleanedText = cleanedText.indexOf("john.doe@example.com");
        int endInCleanedText = startInCleanedText + "john.doe@example.com".length();
        
        // When: Positions from cleaned text are applied to raw HTML
        String context = piiContextExtractor.extractMaskedContext(rawHtml, startInCleanedText, endInCleanedText, "EMAIL");
        
        // Then: Context should be coherent (readable text, not HTML fragments)
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the mask token")
                .contains("[EMAIL]");
            softly.assertThat(context)
                .as("Context should NOT contain raw HTML tags")
                .doesNotContain("<p>")
                .doesNotContain("</p>")
                .doesNotContain("<strong>")
                .doesNotContain("</strong>");
            softly.assertThat(context)
                .as("Context should contain readable surrounding text")
                .containsIgnoringCase("contact us at");
            softly.assertThat(context)
                .as("Context should NOT contain HTML garbage")
                .doesNotContain("ac:")
                .doesNotContain("local-id");
        });
    }

    @Test
    @DisplayName("Should_ExtractCoherentContext_When_ComplexConfluenceHtmlWithPositionsFromCleanedText")
    void Should_ExtractCoherentContext_When_ComplexConfluenceHtmlWithPositionsFromCleanedText() {
        // Given: Complex Confluence HTML with custom attributes (actual bug reproduction)
        String confluenceHtml = """
            <ac:structured-macro ac:name="panel" ac:breakout-width="760" ac:local-id="26967c1d-df67-4">
                <p>Employee information:</p>
                <table>
                    <tr><td>Name</td><td>John Doe</td></tr>
                    <tr><td>Phone</td><td>06 11 22 33 44</td></tr>
                    <tr><td>IP</td><td>192.168.1.100</td></tr>
                </table>
            </ac:structured-macro>
            """;
        
        // Cleaned text (what the detector sees):
        // "Employee information: Name John Doe Phone 06 11 22 33 44 IP 192.168.1.100"
        String cleanedApprox = "Employee information: Name John Doe Phone 06 11 22 33 44 IP 192.168.1.100";
        int phoneStartInCleaned = cleanedApprox.indexOf("06 11 22 33 44");
        int phoneEndInCleaned = phoneStartInCleaned + "06 11 22 33 44".length();
        
        // When: Applying detector positions to raw HTML
        String context = piiContextExtractor.extractMaskedContext(confluenceHtml, phoneStartInCleaned, phoneEndInCleaned, "PHONE");
        
        // Then: Context should be readable, NOT contain Confluence macro garbage
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should contain the mask token")
                .contains("[PHONE]");
            softly.assertThat(context)
                .as("Context should NOT contain Confluence macro attributes")
                .doesNotContain("ac:breakout-width")
                .doesNotContain("ac:local-id")
                .doesNotContain("ac:name")
                .doesNotContain("26967c1d-df67-4");
            softly.assertThat(context)
                .as("Context should NOT contain raw HTML")
                .doesNotContain("<ac:")
                .doesNotContain("</ac:")
                .doesNotContain("<table>")
                .doesNotContain("<tr>");
        });
    }

    @Test
    @DisplayName("Should_ExtractCoherentSensitiveContext_When_PositionsFromCleanedTextAppliedToRawHtml")
    void Should_ExtractCoherentSensitiveContext_When_PositionsFromCleanedTextAppliedToRawHtml() {
        // Given: Raw HTML content
        String rawHtml = "<div class=\"content\"><p>My phone is <b>06 11 22 33 44</b> call me!</p></div>";
        
        // Cleaned text: "My phone is 06 11 22 33 44 call me!"
        String cleanedText = "My phone is 06 11 22 33 44 call me!";
        int startInCleanedText = cleanedText.indexOf("06 11 22 33 44");
        int endInCleanedText = startInCleanedText + "06 11 22 33 44".length();
        
        // When: Extract sensitive (unmasked) context
        String sensitiveContext = piiContextExtractor.extractSensitiveContext(rawHtml, startInCleanedText, endInCleanedText);
        
        // Then: Should contain actual phone number (unmasked) and be readable
        assertSoftly(softly -> {
            softly.assertThat(sensitiveContext)
                .as("Sensitive context should contain the actual PII value")
                .contains("06 11 22 33 44");
            softly.assertThat(sensitiveContext)
                .as("Sensitive context should NOT contain raw HTML")
                .doesNotContain("<div")
                .doesNotContain("<p>")
                .doesNotContain("<b>");
            softly.assertThat(sensitiveContext)
                .as("Sensitive context should be readable text")
                .containsIgnoringCase("phone");
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectlyOnCleanedText_When_MultipleEntitiesWithHtmlSource")
    void Should_MaskCorrectlyOnCleanedText_When_MultipleEntitiesWithHtmlSource() {
        // Given: HTML with multiple PIIs
        String rawHtml = "<p>Contact: <a href=\"mailto:john@test.com\">john@test.com</a> or call <span>+33 6 12 34 56 78</span></p>";
        
        // Cleaned text: "Contact: john@test.com or call +33 6 12 34 56 78"
        String cleanedText = "Contact: john@test.com or call +33 6 12 34 56 78";
        int emailStart = cleanedText.indexOf("john@test.com");
        int emailEnd = emailStart + "john@test.com".length();
        int phoneStart = cleanedText.indexOf("+33 6 12 34 56 78");
        int phoneEnd = phoneStart + "+33 6 12 34 56 78".length();
        
        var entities = List.of(
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build()
        );
        
        // When: Extract context for email with all entities for masking
        String context = piiContextExtractor.extractMaskedContext(rawHtml, emailStart, emailEnd, "EMAIL", entities);
        
        // Then: Both PIIs should be masked in readable context
        assertSoftly(softly -> {
            softly.assertThat(context)
                .as("Context should mask the email")
                .contains("[EMAIL]");
            softly.assertThat(context)
                .as("Context should mask the phone")
                .contains("[PHONE]");
            softly.assertThat(context)
                .as("Context should NOT contain raw HTML")
                .doesNotContain("<p>")
                .doesNotContain("<a ")
                .doesNotContain("href=")
                .doesNotContain("<span>");
            softly.assertThat(context)
                .as("Context should NOT leak actual PII values")
                .doesNotContain("john@test.com")
                .doesNotContain("+33 6 12 34 56 78");
        });
    }

    // =============================================================================
    // REGRESSION TEST: Production bug with secondary entities showing trailing chars
    // =============================================================================
    // This test reproduces the exact production scenario where secondary entities
    // in the same line were NOT properly masked, showing 2-3 trailing characters

    @Test
    @DisplayName("Should_MaskSecondaryEntitiesCompletely_When_MultipleEntitiesOnSameLine")
    void Should_MaskSecondaryEntitiesCompletely_When_MultipleEntitiesOnSameLine() {
        // Given: Production scenario with multiple PIIs on same line
        // Bug manifests when extracting context for ONE entity but OTHER entities are also on the same line
        String source = "- Email: john.doe@example.fr, Phone: 0612345678, IP: 192.168.1.115, Card: 4916632082457636";
        
        // Define all entities on this line (matching production scenario)
        int emailStart = source.indexOf("john.doe@example.fr");
        int emailEnd = emailStart + "john.doe@example.fr".length();
        int phoneStart = source.indexOf("0612345678");
        int phoneEnd = phoneStart + "0612345678".length();
        int ipStart = source.indexOf("192.168.1.115");
        int ipEnd = ipStart + "192.168.1.115".length();
        int cardStart = source.indexOf("4916632082457636");
        int cardEnd = cardStart + "4916632082457636".length();
        
        var allEntities = List.of(
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(ipStart).endPosition(ipEnd).piiType("IP_ADDRESS").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(cardStart).endPosition(cardEnd).piiType("CREDIT_CARD").build()
        );
        
        // When: Extract context for the EMAIL entity (as main entity)
        // The bug was that SECONDARY entities (phone, IP, card) were not properly masked
        String context = piiContextExtractor.extractMaskedContext(source, emailStart, emailEnd, "EMAIL", allEntities);
        
        // Then: ALL entities must be COMPLETELY masked with NO trailing characters
        assertSoftly(softly -> {
            // Verify all tokens are present
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).contains("[IP_ADDRESS]");
            softly.assertThat(context).contains("[CREDIT_CARD]");
            
            // CRITICAL: No trailing characters from email (main entity)
            softly.assertThat(context)
                .as("Main entity (EMAIL) should NOT have trailing chars like [EMAIL]fr")
                .doesNotContain("[EMAIL]fr")
                .doesNotContain("[EMAIL].fr")
                .doesNotContain("[EMAIL]r");
            
            // CRITICAL: No trailing characters from secondary entities (BUG WAS HERE)
            softly.assertThat(context)
                .as("Secondary entity (PHONE) should NOT have trailing chars like [PHONE]78")
                .doesNotContain("[PHONE]78")
                .doesNotContain("[PHONE]8");
            
            softly.assertThat(context)
                .as("Secondary entity (IP_ADDRESS) should NOT have trailing chars like [IP_ADDRESS]15")
                .doesNotContain("[IP_ADDRESS]15")
                .doesNotContain("[IP_ADDRESS]5");
            
            softly.assertThat(context)
                .as("Secondary entity (CREDIT_CARD) should NOT have trailing chars like [CREDIT_CARD]36")
                .doesNotContain("[CREDIT_CARD]36")
                .doesNotContain("[CREDIT_CARD]6");
            
            // Verify NO actual PII values are leaked
            softly.assertThat(context)
                .as("Should NOT leak any actual PII values")
                .doesNotContain("john.doe@example.fr")
                .doesNotContain("0612345678")
                .doesNotContain("192.168.1.115")
                .doesNotContain("4916632082457636");
        });
    }

    // =============================================================================
    // PHASE 2 TESTS: Position-as-Hints Masking Logic
    // =============================================================================
    // These tests validate the new position-as-hints approach where positions
    // from the detector are treated as approximate location hints rather than
    // exact boundaries. The algorithm searches for the exact piiValue near the
    // hint position (±50 chars) and masks the found occurrence.

    @Test
    @DisplayName("Should_MaskExactPiiValue_When_FoundAtExactHintPosition")
    void Should_MaskExactPiiValue_When_FoundAtExactHintPosition() {
        // Given: PII value at exact hint position
        String piiValue = "john.doe@example.com";
        String source = "Contact email: " + piiValue + " for info";
        int start = source.indexOf(piiValue);
        int end = start + piiValue.length();

        // When: Using position-as-hints with exact piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValue);

        // Then: Should mask the exact PII value
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain(piiValue);
            softly.assertThat(context).containsIgnoringCase("contact email");
        });
    }

    @Test
    @DisplayName("Should_FindAndMaskPiiValue_When_HintPositionIsSlightlyOff")
    void Should_FindAndMaskPiiValue_When_HintPositionIsSlightlyOff() {
        // Given: Hint position is off by 5 characters (simulates position mismatch)
        String piiValue = "06 11 22 33 44";
        String source = "Call me at " + piiValue + " tonight";
        int actualStart = source.indexOf(piiValue);
        int actualEnd = actualStart + piiValue.length();
        
        // Simulate detector giving slightly wrong position (off by 5 chars)
        int hintStart = actualStart + 5;
        int hintEnd = actualEnd + 5;

        // When: Using position-as-hints to search for exact piiValue
        String context = piiContextExtractor.extractMaskedContext(source, hintStart, hintEnd, "PHONE", piiValue);

        // Then: Should find and mask the correct occurrence despite position offset
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).doesNotContain(piiValue);
            softly.assertThat(context).doesNotContain("[PHONE]11"); // No trailing chars
            softly.assertThat(context).containsIgnoringCase("call me at");
        });
    }

    @Test
    @DisplayName("Should_SelectClosestOccurrence_When_PiiValueAppearsMultipleTimes")
    void Should_SelectClosestOccurrence_When_PiiValueAppearsMultipleTimes() {
        // Given: Same email appears twice, hint points to second occurrence
        String piiValue = "test@example.com";
        String source = "Primary: test@example.com, Secondary: test@example.com contact";
        
        // Hint points to the SECOND occurrence
        int secondOccurrenceStart = source.lastIndexOf(piiValue);
        int secondOccurrenceEnd = secondOccurrenceStart + piiValue.length();

        // When: Using position-as-hints
        String context = piiContextExtractor.extractMaskedContext(
            source, secondOccurrenceStart, secondOccurrenceEnd, "EMAIL", piiValue);

        // Then: Should mask the second occurrence (closest to hint)
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).containsIgnoringCase("secondary");
            // Context should be around the second occurrence, not the first
        });
    }

    @Test
    @DisplayName("Should_PreserveWhitespace_When_PiiValueHasLeadingOrTrailingSpaces")
    void Should_PreserveWhitespace_When_PiiValueHasLeadingOrTrailingSpaces() {
        // Given: PII value with leading and trailing spaces (as preserved from Phase 1)
        String piiValue = " john.doe@example.com ";
        String source = "Email:" + piiValue + "is valid";
        int start = source.indexOf(piiValue);
        int end = start + piiValue.length();

        // When: Using position-as-hints with whitespace-containing piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValue);

        // Then: Should find and mask the value WITH its whitespace
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("john.doe@example.com");
            // The exact value with spaces should be masked
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_PiiValueAtStartOfText")
    void Should_MaskCorrectly_When_PiiValueAtStartOfText() {
        // Given: PII value at the very start of text
        String piiValue = "admin@company.com";
        String source = piiValue + " is the contact email";
        int start = 0;
        int end = piiValue.length();

        // When: Using position-as-hints
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValue);

        // Then: Should mask correctly even at text start
        assertSoftly(softly -> {
            softly.assertThat(context).startsWith("[EMAIL]");
            softly.assertThat(context).doesNotContain(piiValue);
            softly.assertThat(context).containsIgnoringCase("contact email");
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_PiiValueAtEndOfText")
    void Should_MaskCorrectly_When_PiiValueAtEndOfText() {
        // Given: PII value at the very end of text
        String piiValue = "secret@domain.com";
        String source = "The email address is " + piiValue;
        int start = source.indexOf(piiValue);
        int end = source.length();

        // When: Using position-as-hints
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValue);

        // Then: Should mask correctly even at text end
        assertSoftly(softly -> {
            softly.assertThat(context).endsWith("[EMAIL]");
            softly.assertThat(context).doesNotContain(piiValue);
            softly.assertThat(context).containsIgnoringCase("email address");
        });
    }

    @Test
    @DisplayName("Should_FallbackToHintPositions_When_PiiValueNotFoundInSearchRegion")
    void Should_FallbackToHintPositions_When_PiiValueNotFoundInSearchRegion() {
        // Given: Hint position is correct, but we search for a DIFFERENT piiValue
        String actualPiiInText = "john@example.com";
        String searchForDifferentValue = "jane@example.com"; // Not in text
        String source = "Contact: " + actualPiiInText + " for help";
        int start = source.indexOf(actualPiiInText);
        int end = start + actualPiiInText.length();

        // When: Searching for value not in text (edge case/data mismatch)
        String context = piiContextExtractor.extractMaskedContext(
            source, start, end, "EMAIL", searchForDifferentValue);

        // Then: Should fallback to using hint positions (original behavior)
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            // Will use hint positions as-is, might mask the actual text at that position
        });
    }

    @Test
    @DisplayName("Should_HandleGracefully_When_PiiValueIsNull")
    void Should_HandleGracefully_When_PiiValueIsNull() {
        // Given: piiValue is null (should fallback to old behavior)
        String source = "Contact: john@example.com for info";
        int start = source.indexOf("john@example.com");
        int end = start + "john@example.com".length();

        // When: Using position-as-hints with null piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", (String) null);

        // Then: Should fallback to position-based masking (old behavior)
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("john@example.com");
        });
    }

    @Test
    @DisplayName("Should_HandleGracefully_When_PiiValueIsEmpty")
    void Should_HandleGracefully_When_PiiValueIsEmpty() {
        // Given: piiValue is empty string
        String source = "Contact: john@example.com for info";
        int start = source.indexOf("john@example.com");
        int end = start + "john@example.com".length();

        // When: Using position-as-hints with empty piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", "");

        // Then: Should fallback to position-based masking
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("john@example.com");
        });
    }

    @Test
    @DisplayName("Should_HandleGracefully_When_PiiValueIsBlank")
    void Should_HandleGracefully_When_PiiValueIsBlank() {
        // Given: piiValue is blank (only whitespace)
        String source = "Contact: john@example.com for info";
        int start = source.indexOf("john@example.com");
        int end = start + "john@example.com".length();

        // When: Using position-as-hints with blank piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", "   ");

        // Then: Should fallback to position-based masking
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("john@example.com");
        });
    }

    @Test
    @DisplayName("Should_MaskAllEntities_When_MultipleEntitiesWithPositionAsHints")
    void Should_MaskAllEntities_When_MultipleEntitiesWithPositionAsHints() {
        // Given: Multiple PIIs on same line, each with their exact piiValues
        String email = "contact@test.com";
        String phone = "0601020304";
        String source = "Call " + phone + " or email " + email + " today";
        
        int phoneStart = source.indexOf(phone);
        int phoneEnd = phoneStart + phone.length();
        int emailStart = source.indexOf(email);
        int emailEnd = emailStart + email.length();
        
        var allEntities = List.of(
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE")
                .sensitiveValue(phone).build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL")
                .sensitiveValue(email).build()
        );

        // When: Extract context for phone with position-as-hints
        String context = piiContextExtractor.extractMaskedContext(
            source, phoneStart, phoneEnd, "PHONE", phone, allEntities);

        // Then: Both entities should be completely masked
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain(phone);
            softly.assertThat(context).doesNotContain(email);
            softly.assertThat(context).doesNotContain("[PHONE]04"); // No trailing chars
            softly.assertThat(context).doesNotContain("[EMAIL]om"); // No trailing chars
        });
    }

    @Test
    @DisplayName("Should_MaskSecondaryEntities_When_UsingPositionAsHintsForMainEntity")
    void Should_MaskSecondaryEntities_When_UsingPositionAsHintsForMainEntity() {
        // Given: Main entity uses position-as-hints, secondary entities also on same line
        String mainEmail = "primary@test.fr";
        String secondaryPhone = "0612345678";
        String secondaryIp = "10.0.0.1";
        String source = "Info: " + mainEmail + ", phone " + secondaryPhone + ", IP " + secondaryIp;
        
        int emailStart = source.indexOf(mainEmail);
        int emailEnd = emailStart + mainEmail.length();
        int phoneStart = source.indexOf(secondaryPhone);
        int phoneEnd = phoneStart + secondaryPhone.length();
        int ipStart = source.indexOf(secondaryIp);
        int ipEnd = ipStart + secondaryIp.length();
        
        var allEntities = List.of(
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(emailStart).endPosition(emailEnd).piiType("EMAIL").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(phoneStart).endPosition(phoneEnd).piiType("PHONE").build(),
            DetectedPersonallyIdentifiableInformation.builder()
                .startPosition(ipStart).endPosition(ipEnd).piiType("IP_ADDRESS").build()
        );

        // When: Extract context for email (main entity) using position-as-hints
        String context = piiContextExtractor.extractMaskedContext(
            source, emailStart, emailEnd, "EMAIL", mainEmail, allEntities);

        // Then: ALL entities including secondary ones should be completely masked
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).contains("[IP_ADDRESS]");
            
            // No trailing characters from any entity
            softly.assertThat(context)
                .as("Main entity should not have trailing chars")
                .doesNotContain("[EMAIL]fr")
                .doesNotContain("[EMAIL].fr");
            
            softly.assertThat(context)
                .as("Secondary phone should not have trailing chars")
                .doesNotContain("[PHONE]78")
                .doesNotContain("[PHONE]8");
            
            softly.assertThat(context)
                .as("Secondary IP should not have trailing chars")
                .doesNotContain("[IP_ADDRESS].1")
                .doesNotContain("[IP_ADDRESS]1");
            
            // No actual values leaked
            softly.assertThat(context).doesNotContain(mainEmail);
            softly.assertThat(context).doesNotContain(secondaryPhone);
            softly.assertThat(context).doesNotContain(secondaryIp);
        });
    }

    @Test
    @DisplayName("Should_SearchInLargeRegion_When_HintPositionFarFromActualPosition")
    void Should_SearchInLargeRegion_When_HintPositionFarFromActualPosition() {
        // Given: Hint position is 40 characters away from actual position (within ±50 search radius)
        String piiValue = "sensitive@email.com";
        String source = "Text text text text text text " + piiValue + " more text more text";
        int actualStart = source.indexOf(piiValue);
        int actualEnd = actualStart + piiValue.length();
        
        // Hint is 40 chars before actual position (still within ±50 search radius)
        int hintStart = actualStart - 40;
        int hintEnd = actualEnd - 40;

        // When: Using position-as-hints with large offset
        String context = piiContextExtractor.extractMaskedContext(
            source, hintStart, hintEnd, "EMAIL", piiValue);

        // Then: Should still find and mask the value within ±50 char search region
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain(piiValue);
        });
    }

    @Test
    @DisplayName("Should_UseFallback_When_HintPositionBeyondSearchRadius")
    void Should_UseFallback_When_HintPositionBeyondSearchRadius() {
        // Given: Hint position is MORE than 50 characters away (beyond search radius)
        String piiValue = "far@away.com";
        String padding = "X".repeat(60); // 60 chars away
        String source = padding + piiValue + " text";

        // Hint is at position 0 (60+ chars before actual position, beyond ±50 radius)
        int hintStart = 0;
        int hintEnd = piiValue.length();

        // When: Using position-as-hints with offset beyond search radius
        String context = piiContextExtractor.extractMaskedContext(
            source, hintStart, hintEnd, "EMAIL", piiValue);

        // Then: Won't find the value, will fallback to hint positions
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            // Will use fallback behavior (mask at hint position)
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_PiiValueContainsSpecialCharacters")
    void Should_MaskCorrectly_When_PiiValueContainsSpecialCharacters() {
        // Given: PII value with special regex characters
        String piiValue = "user+test@example.com";
        String source = "Email: " + piiValue + " is valid";
        int start = source.indexOf(piiValue);
        int end = start + piiValue.length();

        // When: Using position-as-hints with special chars
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValue);

        // Then: Should handle special chars correctly (not treat + as regex operator)
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain(piiValue);
        });
    }

    // =============================================================================
    // PHASE 2 BIS TESTS: Whitespace Normalization for Position-as-Hints
    // =============================================================================
    // These tests validate the normalized whitespace matching strategy.
    // When the detector extracts a value with different whitespace than the source
    // (e.g., "06 11 22" from source "06  11  22"), the algorithm normalizes both
    // and finds the correct position to mask.

    @Test
    @DisplayName("Should_FindPiiValue_When_SourceHasExtraWhitespace")
    void Should_FindPiiValue_When_SourceHasExtraWhitespace() {
        // Given: Source has extra whitespace between digits, but detector normalized it
        // Detector extracted: "06 11 22 33 44" (normalized)
        // Source contains:    "06  11  22  33  44" (with extra spaces)
        String piiValueFromDetector = "06 11 22 33 44";
        String piiValueInSource = "06  11  22  33  44";
        String source = "Phone: " + piiValueInSource + " call me";
        int start = source.indexOf(piiValueInSource);
        int end = start + piiValueFromDetector.length(); // Detector provides length based on normalized value

        // When: Using position-as-hints with normalized piiValue
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "PHONE", piiValueFromDetector);

        // Then: Should find and mask the value despite whitespace differences
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).doesNotContain("06");
            softly.assertThat(context).doesNotContain("11");
            softly.assertThat(context).doesNotContain("22");
            softly.assertThat(context).doesNotContain("33");
            softly.assertThat(context).doesNotContain("44");
            softly.assertThat(context).containsIgnoringCase("phone:");
        });
    }

    @Test
    @DisplayName("Should_FindPiiValue_When_SearchValueHasExtraWhitespace")
    void Should_FindPiiValue_When_SearchValueHasExtraWhitespace() {
        // Given: Detector value has extra whitespace, source is normalized
        // This is less common but the normalization should handle both directions
        String piiValueFromDetector = "john.doe@example.com";
        String source = "Email: " + piiValueFromDetector + " for info";
        int start = source.indexOf(piiValueFromDetector);
        int end = start + piiValueFromDetector.length();

        // When: Using position-as-hints
        String context = piiContextExtractor.extractMaskedContext(source, start, end, "EMAIL", piiValueFromDetector);

        // Then: Should mask correctly
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("john.doe@example.com");
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_WhitespaceNormalizationNeeded")
    void Should_MaskCorrectly_When_WhitespaceNormalizationNeeded() {
        // Given: Real-world scenario - phone number with varying whitespace
        String detectorValue = "06 12 34 56 78"; // Detector extracted with single spaces
        String sourceValue = "06  12   34  56  78"; // Source has irregular spacing
        String source = "Appelez-moi au " + sourceValue + " merci";
        
        // Detector provides positions based on its extracted value
        int hintStart = source.indexOf("06");
        int hintEnd = hintStart + detectorValue.length();

        // When: Masking with normalized search
        String context = piiContextExtractor.extractMaskedContext(source, hintStart, hintEnd, "PHONE", detectorValue);

        // Then: Should mask the entire phone number including extra spaces
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).doesNotContain("06");
            softly.assertThat(context).doesNotContain("12");
            softly.assertThat(context).doesNotContain("34");
            softly.assertThat(context).doesNotContain("56");
            softly.assertThat(context).doesNotContain("78");
            softly.assertThat(context).containsIgnoringCase("appelez-moi au");
        });
    }

    @Test
    @DisplayName("Should_MaskCorrectly_When_TabsAndNewlinesNormalized")
    void Should_MaskCorrectly_When_TabsAndNewlinesNormalized() {
        // Given: Source has tabs/newlines that get normalized to spaces
        String detectorValue = "John Doe";
        String sourceValue = "John\t\tDoe"; // Source has tabs instead of spaces
        String source = "Name: " + sourceValue + " registered";
        int hintStart = source.indexOf("John");
        int hintEnd = hintStart + detectorValue.length();

        // When: Using normalized matching
        String context = piiContextExtractor.extractMaskedContext(source, hintStart, hintEnd, "PERSON", detectorValue);

        // Then: Should find and mask despite whitespace type difference
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PERSON]");
            softly.assertThat(context).doesNotContain("John");
            softly.assertThat(context).doesNotContain("Doe");
        });
    }

    @Test
    @DisplayName("Should_PreferExactMatch_When_BothExactAndNormalizedPossible")
    void Should_PreferExactMatch_When_BothExactAndNormalizedPossible() {
        // Given: Source contains both exact value and similar value with different whitespace
        String piiValue = "test@example.com";
        String source = "Primary: test@example.com, also: test @example.com here";
        int exactStart = source.indexOf(piiValue);
        int exactEnd = exactStart + piiValue.length();

        // When: Searching for exact value
        String context = piiContextExtractor.extractMaskedContext(source, exactStart, exactEnd, "EMAIL", piiValue);

        // Then: Should prefer the exact match
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).doesNotContain("test@example.com");
            // Should mask the first (exact) occurrence, not the spaced one
            softly.assertThat(context).containsIgnoringCase("primary:");
        });
    }

    @Test
    @DisplayName("Should_HandleLeadingAndTrailingWhitespace_When_Normalizing")
    void Should_HandleLeadingAndTrailingWhitespace_When_Normalizing() {
        // Given: Detector value has leading/trailing whitespace trimmed
        String detectorValue = "secret123";
        String sourceValue = "  secret123  "; // Source has leading/trailing spaces
        String source = "Password:" + sourceValue + "end";
        int hintStart = source.indexOf("secret");
        int hintEnd = hintStart + detectorValue.length();

        // When: Using normalized search
        String context = piiContextExtractor.extractMaskedContext(source, hintStart, hintEnd, "PASSWORD", detectorValue);

        // Then: Should find the value ignoring leading/trailing spaces
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PASSWORD]");
            softly.assertThat(context).doesNotContain("secret123");
        });
    }

    @Test
    @DisplayName("Should_SelectClosestMatch_When_MultipleNormalizedMatches")
    void Should_SelectClosestMatch_When_MultipleNormalizedMatches() {
        // Given: Same value appears multiple times with different whitespace
        String detectorValue = "06 11 22";
        String source = "First: 06  11  22, Second: 06 11 22, Third: 06   11   22";
        
        // Hint points to the SECOND occurrence (exact match)
        int secondStart = source.indexOf("Second:") + "Second: ".length();
        int secondEnd = secondStart + detectorValue.length();

        // When: Using position-as-hints with multiple possible matches
        String context = piiContextExtractor.extractMaskedContext(source, secondStart, secondEnd, "PHONE", detectorValue);

        // Then: Should select the closest match (the exact one at hint position)
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PHONE]");
            softly.assertThat(context).containsIgnoringCase("second:");
        });
    }

    @Test
    @DisplayName("Should_NotMatch_When_WhitespaceDifferenceChangesSemantics")
    void Should_NotMatch_When_WhitespaceDifferenceChangesSemantics() {
        // Given: Whitespace difference that shouldn't match (different words)
        String detectorValue = "John Smith"; // Two words
        String source = "Contact: JohnSmith and others"; // One word (no space)
        int hintStart = source.indexOf("JohnSmith");
        int hintEnd = hintStart + "JohnSmith".length();

        // When: Searching for "John Smith" (with space)
        String context = piiContextExtractor.extractMaskedContext(source, hintStart, hintEnd, "PERSON", detectorValue);

        // Then: Should NOT match (whitespace removal changes meaning)
        // Will fallback to position-based masking
        assertSoftly(softly -> {
            softly.assertThat(context).contains("[PERSON]");
            // The fallback should still mask something at the hint position
        });
    }

}
