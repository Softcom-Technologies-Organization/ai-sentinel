package pro.softcom.sentinelle.application.pii.reporting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.application.pii.reporting.config.PiiContextProperties;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link PiiContextExtractor}.
 * <p>
 * Verifies extraction, masking and truncation of PII context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PiiContextExtractor - PII context extraction")
class PiiContextExtractorTest {

    @Mock
    private ContentParserFactory parserFactory;

    @Mock
    private PiiContextProperties contextProperties;

    private PiiContextExtractor piiContextExtractor;
    
    private ContentParser plainTextParser;

    @BeforeEach
    void setUp() {
        // Use real PlainTextParser for testing
        plainTextParser = new PlainTextParser();
        
        // Configure mocks with lenient() to allow unused stubbings in some tests
        lenient().when(contextProperties.getMaxLength()).thenReturn(200);
        lenient().when(contextProperties.getSideLength()).thenReturn(80);
        lenient().when(parserFactory.getParser(anyString())).thenReturn(plainTextParser);
        
        // Create instance with mocked dependencies
        piiContextExtractor = new PiiContextExtractor(parserFactory, contextProperties);
    }

    @ParameterizedTest(name = "{index} -> should mask occurrence and keep line snippet for type={2}")
    @MethodSource("basicContextCases")
    @DisplayName("Should_ExtractAndMaskContext_BasicCases")
    void Should_ExtractAndMaskContext_BasicCases(String source, String occurrence, String type) {
        int start = source.indexOf(occurrence);
        int end = start + occurrence.length();
        String ctx = piiContextExtractor.extract(source, start, end, type);
        assertThat(ctx).contains("[" + type + "]");
        assertThat(ctx).doesNotContain(occurrence);
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
        String ctx = piiContextExtractor.extract(source, start, end, "EMAIL");

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
                .context(existingContext)
                .build();

        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-1")
                .sourceContent("My email is john.doe@example.com and my phone")
                .detectedEntities(List.of(entity))
                .build();

        // When
        ScanResult result = piiContextExtractor.enrichContexts(scanResult);

        // Then
        assertThat(result.detectedEntities().getFirst().context())
                .isEqualTo(existingContext);
    }

    @Test
    @DisplayName("Should_ReturnNull_When_SourceContentIsNull")
    void Should_ReturnNull_When_SourceContentIsNull() {
        // When
        String ctx = piiContextExtractor.extract(null, 0, 10, "EMAIL");
        // Then
        assertThat(ctx).isNull();
    }



    @Test
    @DisplayName("Should_TruncateContext_When_ExceedsMaxLength")
    void Should_TruncateContext_When_ExceedsMaxLength() {
        // Given: create a long text with PII in the middle
        String longPrefix = "A".repeat(150);
        String piiValue = "john.doe@example.com";
        String longSuffix = "B".repeat(150);
        String source = longPrefix + piiValue + longSuffix;
        int start = longPrefix.length();
        int end = start + piiValue.length();

        // When
        String context = piiContextExtractor.extract(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).isNotNull();
            softly.assertThat(context.length()).isLessThanOrEqualTo(200 + 2); // +2 for ellipses
            softly.assertThat(context).contains("[EMAIL]");
            softly.assertThat(context).matches(".*….*"); // Contains at least one ellipsis
        });
    }

    @Test
    @DisplayName("Should_CompactWhitespace_When_ExtractingContext")
    void Should_CompactWhitespace_When_ExtractingContext() {
        // Given
        String source = "My   email    is\n\tjohn.doe@example.com   and   my   phone";
        int start = 21;
        int end = 41;

        // When
        String context = piiContextExtractor.extract(source, start, end, "EMAIL");

        // Then
        assertSoftly(softly -> {
            softly.assertThat(context).doesNotContain("  "); // No double spaces
            softly.assertThat(context).doesNotContain("\n");
            softly.assertThat(context).doesNotContain("\t");
        });
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
        String context = piiContextExtractor.extract(source, start, end, "EMAIL");

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
        String ctx = piiContextExtractor.extract(source, start, end, null);

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
        String ctx = piiContextExtractor.extract(source, start, end, "   ");

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
        String emailCtx = piiContextExtractor.extract(source, emailStart, emailEnd, "EMAIL");
        String phoneCtx = piiContextExtractor.extract(source, phoneStart, phoneEnd, "PHONE");

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
        String context = piiContextExtractor.extract(source, start, end, "EMAIL");

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
        String context = piiContextExtractor.extract(source, start, end, "EMAIL");

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
        String ctx = piiContextExtractor.extract(source, start, end, "EMAIL");

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
        String ctx = piiContextExtractor.extract(source, start, end, "EMAIL");

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
        String ctx = piiContextExtractor.extract(source, emailStart, emailEnd, "EMAIL", entities);

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
            softly.assertThat(enrichedEmail.context()).isNotNull();
            softly.assertThat(enrichedEmail.context()).contains("[EMAIL]");
            softly.assertThat(enrichedEmail.context()).contains("[PHONE]");
            softly.assertThat(enrichedEmail.context()).doesNotContain("john@example.com");
            softly.assertThat(enrichedEmail.context()).doesNotContain("06 11 22 33 44");
            
            // Phone context should mask both EMAIL and PHONE
            softly.assertThat(enrichedPhone.context()).isNotNull();
            softly.assertThat(enrichedPhone.context()).contains("[EMAIL]");
            softly.assertThat(enrichedPhone.context()).contains("[PHONE]");
            softly.assertThat(enrichedPhone.context()).doesNotContain("john@example.com");
            softly.assertThat(enrichedPhone.context()).doesNotContain("06 11 22 33 44");
        });
    }
}
