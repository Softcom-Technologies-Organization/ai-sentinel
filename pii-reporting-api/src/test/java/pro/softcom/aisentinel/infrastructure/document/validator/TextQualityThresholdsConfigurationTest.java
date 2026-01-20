package pro.softcom.aisentinel.infrastructure.document.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pro.softcom.aisentinel.infrastructure.document.config.TextQualityThresholds;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration tests for TextQualityThresholds.
 * Verifies that all configurable properties are correctly respected by the validator.
 */
@DisplayName("TextQualityThresholds Configuration Tests")
class TextQualityThresholdsConfigurationTest {

    @ParameterizedTest
    @CsvSource({
            "20, 12345678901234567890, false",  // exactly 20 chars -> should NOT be too short
            "20, 1234567890123456789, true",    // 19 chars -> should be too short
            "30, 12345678901234567890, true",   // 20 chars < 30 -> should be too short
            "10, 12345678901234567890, false"   // 20 chars >= 10 -> should NOT be too short
    })
    @DisplayName("Should_RespectMinTextLength_When_Configured")
    void Should_RespectMinTextLength_When_Configured(int minLength, String text, boolean expectTooShort) {
        // Given
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMinTextLength(minLength);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When
        boolean result = validator.isTooShort(text);

        // Then
        assertThat(result)
                .as("Text length %d with threshold %d should%s be too short",
                        text.length(), minLength, expectTooShort ? "" : " NOT")
                .isEqualTo(expectTooShort);
    }

    @ParameterizedTest
    @CsvSource({
            "0.3, aaa!!!!!!, false",   // 3/10 = 30% (at threshold) -> OK
            "0.3, aa!!!!!!!, true",    // 2/10 = 20% (below threshold) -> LOW
            "0.5, aaaaa!!!!!, false",  // 5/10 = 50% (at threshold) -> OK
            "0.5, aaaa!!!!!!, true",   // 4/10 = 40% (below threshold) -> LOW
            "0.1, a!!!!!!!!!, false"   // 1/10 = 10% (at threshold) -> OK
    })
    @DisplayName("Should_RespectMinAlphanumericRatio_When_Configured")
    void Should_RespectMinAlphanumericRatio_When_Configured(double minRatio, String text, boolean expectLowRatio) {
        // Given
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMinAlphanumericRatio(minRatio);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When
        boolean result = validator.hasLowAlphanumericRatio(text);

        // Then
        assertThat(result)
                .as("Text with threshold %.2f should%s have low alphanumeric ratio",
                        minRatio, expectLowRatio ? "" : " NOT")
                .isEqualTo(expectLowRatio);
    }

    @Test
    @DisplayName("Should_RespectMinSpaceRatio_When_Configured")
    void Should_RespectMinSpaceRatio_When_Configured() {
        // Given - Require 10% spaces
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMinSpaceRatio(0.1);
        properties.setMinLengthForSpaceCheck(50);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When - Text with 15% spaces (above threshold)
        String textWithSpaces = "word word word word word word word word word word word";
        boolean hasInsufficientSpacesAbove = validator.hasInsufficientSpacing(textWithSpaces);

        // Then
        assertThat(hasInsufficientSpacesAbove)
                .as("Text with 15%% spaces should NOT have insufficient spacing (threshold 10%%)")
                .isFalse();

        // When - Text with 3% spaces (below threshold)
        String textFewSpaces = "w w " + "w".repeat(100);
        boolean hasInsufficientSpacesBelow = validator.hasInsufficientSpacing(textFewSpaces);

        // Then
        assertThat(hasInsufficientSpacesBelow)
                .as("Text with 3%% spaces should have insufficient spacing (threshold 10%%)")
                .isTrue();
    }

    @Test
    @DisplayName("Should_RespectMinPrintableRatio_When_Configured")
    void Should_RespectMinPrintableRatio_When_Configured() {
        // Given - Require 50% printable
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMinPrintableRatio(0.5);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When - Text with 60% printable (above threshold)
        String textAbove = "abc\u0000\u0001";  // 3/5 = 60%
        boolean hasTooManyControlCharsAbove = validator.hasTooManyControlCharacters(textAbove);

        // Then
        assertThat(hasTooManyControlCharsAbove)
                .as("Text with 60%% printable should NOT have too many control chars (threshold 50%%)")
                .isFalse();

        // When - Text with 40% printable (below threshold)
        String textBelow = "ab\u0000\u0001\u0002";  // 2/5 = 40%
        boolean hasTooManyControlCharsBelow = validator.hasTooManyControlCharacters(textBelow);

        // Then
        assertThat(hasTooManyControlCharsBelow)
                .as("Text with 40%% printable should have too many control chars (threshold 50%%)")
                .isTrue();
    }

    @Test
    @DisplayName("Should_RespectMaxSpecialCharRatio_When_Configured")
    void Should_RespectMaxSpecialCharRatio_When_Configured() {
        // Given - Max 20% special chars
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMaxSpecialCharRatio(0.2);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When - Text with 9% special (below threshold)
        String textBelow = "aaaaaaaaaa!";  // 1/11 = ~9%
        boolean hasExcessiveBelow = validator.hasExcessiveSpecialCharacters(textBelow);

        // Then
        assertThat(hasExcessiveBelow)
                .as("Text with 9%% special chars should NOT have excessive (threshold 20%%)")
                .isFalse();

        // When - Text with 50% special (above threshold)
        String textAbove = "aaaaa!!!!!";  // 5/10 = 50%
        boolean hasExcessiveAbove = validator.hasExcessiveSpecialCharacters(textAbove);

        // Then
        assertThat(hasExcessiveAbove)
                .as("Text with 50%% special chars should have excessive (threshold 20%%)")
                .isTrue();
    }

    @Test
    @DisplayName("Should_RespectMinLengthForSpaceCheck_When_Configured")
    void Should_RespectMinLengthForSpaceCheck_When_Configured() {
        // Given - Space check only for text > 20 chars, require 10% spaces
        TextQualityThresholds properties = new TextQualityThresholds();
        properties.setMinLengthForSpaceCheck(20);
        properties.setMinSpaceRatio(0.1);
        TextQualityValidator validator = new TextQualityValidator(properties);

        // When - Text with 20 chars (at threshold), no spaces
        String textAtThreshold = "abcdefghijklmnopqrst";  // 20 chars = threshold
        boolean triggersCheckAtThreshold = validator.hasInsufficientSpacing(textAtThreshold);

        // Then - Should NOT trigger check (need > 20, not >= 20)
        assertThat(triggersCheckAtThreshold)
                .as("Text with 20 chars should NOT trigger space check (threshold 20)")
                .isFalse();

        // When - Text with 21 chars (above threshold), no spaces
        String textAboveThreshold = "abcdefghijklmnopqrstu";  // 21 chars > threshold
        boolean triggersCheckAbove = validator.hasInsufficientSpacing(textAboveThreshold);

        // Then - Should trigger check and fail (0% spaces < 10%)
        assertThat(triggersCheckAbove)
                .as("Text with 21 chars and 0%% spaces should have insufficient spacing (threshold 10%%)")
                .isTrue();
    }
}
