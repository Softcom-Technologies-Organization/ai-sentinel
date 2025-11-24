package pro.softcom.aisentinel.infrastructure.document.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pro.softcom.aisentinel.infrastructure.document.config.TextQualityThresholds;

@DisplayName("TextQualityValidator - Human-Readable Text Detection")
class TextQualityValidatorTest {

    private TextQualityValidator validator;

    @BeforeEach
    void setUp() {
        // Given
        TextQualityThresholds properties = new TextQualityThresholds();
        validator = new TextQualityValidator(properties);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "  \t  \n  "})
    @DisplayName("Should_DetectAsImageOnly_When_TextIsNullOrBlank")
    void Should_DetectAsImageOnly_When_TextIsNullOrBlank(String blankText) {
        // When
        boolean result = validator.isImageOnlyDocument(blankText);

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "Short text",
            "12345678901234567890",  // 20 chars < 50
            "This is still too short for validation"  // 40 chars < 50
    })
    @DisplayName("Should_DetectAsImageOnly_When_TextTooShort")
    void Should_DetectAsImageOnly_When_TextTooShort(String shortText) {
        // When
        boolean isTooShort = validator.isTooShort(shortText);
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(shortText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(isTooShort)
                    .as("Text should be considered too short")
                    .isTrue();
            softly.assertThat(isImageOnlyDocument)
                    .as("Text should be detected as image-only")
                    .isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "!!!@@@###$$$%%%^^^&&&***((()))____----====++++||||\\\\\\///...",
            "<<>>||&&%%$$##@@!!**++==--__::;;''\"\"{{}}[][]",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
    })
    @DisplayName("Should_DetectAsImageOnly_When_LowAlphanumericRatio")
    void Should_DetectAsImageOnly_When_LowAlphanumericRatio(String corruptedText) {
        // When
        boolean hasLowRatio = validator.hasLowAlphanumericRatio(corruptedText);
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(corruptedText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(hasLowRatio)
                    .as("Text should have low alphanumeric ratio")
                    .isTrue();
            softly.assertThat(isImageOnlyDocument)
                    .as("Text should be detected as image-only")
                    .isTrue();
        });
    }

    @Test
    @DisplayName("Should_DetectAsImageOnly_When_NoProperSpacing")
    void Should_DetectAsImageOnly_When_NoProperSpacing() {
        // Given - Long text without spaces (OCR failure pattern)
        String noSpaceText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890" +
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890" +
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // When
        boolean hasInsufficientSpacing = validator.hasInsufficientSpacing(noSpaceText);
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(noSpaceText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(hasInsufficientSpacing)
                    .as("Text should have insufficient spacing")
                    .isTrue();
            softly.assertThat(isImageOnlyDocument)
                    .as("Text should be detected as image-only")
                    .isTrue();
        });
    }

    @Test
    @DisplayName("Should_DetectAsImageOnly_When_ExcessiveSpecialChars")
    void Should_DetectAsImageOnly_When_ExcessiveSpecialChars() {
        // Given - More than 40% special chars (OCR artifacts)
        String artifactText = "txt!!@@##$$%%^^&&**(())__--==++||\\///...txt!!@@##$$%%^^&&**(())__--==++||\\///";

        // When
        boolean hasExcessiveSpecialChars = validator.hasExcessiveSpecialCharacters(artifactText);
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(artifactText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(hasExcessiveSpecialChars)
                    .as("Text should have excessive special characters")
                    .isTrue();
            softly.assertThat(isImageOnlyDocument)
                    .as("Text should be detected as image-only")
                    .isTrue();
        });
    }

    @Test
    @DisplayName("Should_DetectAsImageOnly_When_ManyControlCharacters")
    void Should_DetectAsImageOnly_When_ManyControlCharacters() {
        // Given - Text with non-printable characters (< 80% printable)
        String controlCharsText = "Some text\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000B\u000C\u000E\u000F";

        // When
        boolean hasTooManyControlChars = validator.hasTooManyControlCharacters(controlCharsText);
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(controlCharsText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(hasTooManyControlChars)
                    .as("Text should have too many control characters")
                    .isTrue();
            softly.assertThat(isImageOnlyDocument)
                    .as("Text should be detected as image-only")
                    .isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "This is a normal text document with proper sentences and spacing. It contains enough text to be considered valid.",
            "Ceci est un document en français avec des accents éèêë et des caractères spéciaux. Il contient suffisamment de texte.",
            "Dies ist ein deutscher Text mit Umlauten äöü und ß. Er enthält genügend Text um als gültig zu gelten.",
            "This document contains numbers 123456 and punctuation! But it's still valid, readable text with good structure."
    })
    @DisplayName("Should_NotDetectAsImageOnly_When_ValidText")
    void Should_NotDetectAsImageOnly_When_ValidText(String validText) {
        // When
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(validText);
        boolean isTooShort = validator.isTooShort(validText);
        boolean hasLowRatio = validator.hasLowAlphanumericRatio(validText);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(isImageOnlyDocument)
                    .as("Valid text should not be detected as image-only")
                    .isFalse();
            softly.assertThat(isTooShort)
                    .as("Valid text should not be too short")
                    .isFalse();
            softly.assertThat(hasLowRatio)
                    .as("Valid text should not have low alphanumeric ratio")
                    .isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "The configuration file uses JSON format: {\"key\": \"value\", \"number\": 42}. This is normal for technical documentation.",
            "Contact us at support@example.com or visit https://example.com for more information about our products.",
            "Important points:\n• First item in list\n• Second item with details\n• Third item\nAll items are important."
    })
    @DisplayName("Should_NotDetectAsImageOnly_When_TechnicalOrFormattedText")
    void Should_NotDetectAsImageOnly_When_TechnicalOrFormattedText(String text) {
        // When
        boolean isImageOnlyDocument = validator.isImageOnlyDocument(text);

        // Then
        assertThat(isImageOnlyDocument)
                .as("Technical or formatted text should not be detected as image-only")
                .isFalse();
    }

    @Test
    @DisplayName("Should_NotTriggerSpaceCheck_When_TextTooShortForSpaceRule")
    void Should_NotTriggerSpaceCheck_When_TextTooShortForSpaceRule() {
        // Given - Text shorter than minLengthForSpaceCheck (100 chars)
        String shortText = "NoSpacesHereButStillShort1234567890";

        // When
        boolean hasInsufficientSpacing = validator.hasInsufficientSpacing(shortText);

        // Then
        assertThat(hasInsufficientSpacing)
                .as("Short text should not trigger space check rule")
                .isFalse();
    }

    @Test
    @DisplayName("Should_AllowCustomThresholds_When_PropertiesConfigured")
    void Should_AllowCustomThresholds_When_PropertiesConfigured() {
        // Given - Custom properties with lower thresholds
        TextQualityThresholds customProperties = new TextQualityThresholds();
        customProperties.setMinTextLength(20);
        customProperties.setMinAlphanumericRatio(0.1);
        TextQualityValidator customValidator = new TextQualityValidator(customProperties);

        // When - Text is exactly at threshold (20 chars)
        String textAtThreshold = "12345678901234567890";
        boolean isTooShortAtThreshold = customValidator.isTooShort(textAtThreshold);

        // Then - Should NOT be too short
        assertThat(isTooShortAtThreshold)
                .as("Text at threshold should not be considered too short")
                .isFalse();

        // When - Text is below threshold (19 chars)
        String textBelowThreshold = "1234567890123456789";
        boolean isTooShortBelowThreshold = customValidator.isTooShort(textBelowThreshold);

        // Then - Should be too short
        assertThat(isTooShortBelowThreshold)
                .as("Text below threshold should be considered too short")
                .isTrue();
    }
}
