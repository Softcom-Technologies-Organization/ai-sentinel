package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TikaAttachmentTextExtractorAdapter - Image-Only Detection")
class TikaAttachmentTextExtractorAdapterTest {

    private final TikaAttachmentTextExtractorAdapter adapter = new TikaAttachmentTextExtractorAdapter();

    @Test
    @DisplayName("Should detect null or blank text as image-only")
    void Should_ReturnTrue_When_TextIsNullOrBlank() throws Exception {
        assertThat(invokeLooksImageOnly(null)).isTrue();
        assertThat(invokeLooksImageOnly("")).isTrue();
        assertThat(invokeLooksImageOnly("   ")).isTrue();
        assertThat(invokeLooksImageOnly("\t\n")).isTrue();
    }

    @Test
    @DisplayName("Should detect very short text as image-only")
    void Should_ReturnTrue_When_TextTooShort() throws Exception {
        assertThat(invokeLooksImageOnly("abc")).isTrue();
        assertThat(invokeLooksImageOnly("Short text")).isTrue();
        assertThat(invokeLooksImageOnly("12345678901234567890")).isTrue(); // < 50 chars
    }

    @Test
    @DisplayName("Should detect text with low alphanumeric ratio as image-only")
    void Should_ReturnTrue_When_LowAlphanumericRatio() throws Exception {
        // Text with lots of special chars, very few letters/digits
        String corruptedText = "!!!@@@###$$$%%%^^^&&&***((()))____----====++++||||\\\\\\///...";
        assertThat(invokeLooksImageOnly(corruptedText)).isTrue();
    }

    @Test
    @DisplayName("Should detect text without spaces as image-only")
    void Should_ReturnTrue_When_NoProperSpacing() throws Exception {
        // Long text without spaces (OCR failure pattern) - needs to be longer to trigger rule
        String noSpaceText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890" +
                            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890" +
                            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        assertThat(invokeLooksImageOnly(noSpaceText)).isTrue();
    }

    @Test
    @DisplayName("Should detect text with excessive special characters as image-only")
    void Should_ReturnTrue_When_ExcessiveSpecialChars() throws Exception {
        // More than 40% special chars suggests OCR artifacts
        // Need to ensure special chars are > 40% of total
        String artifactText = "txt!!@@##$$%%^^&&**(())__--==++||\\///...txt!!@@##$$%%^^&&**(())__--==++||\\///";
        assertThat(invokeLooksImageOnly(artifactText)).isTrue();
    }

    @Test
    @DisplayName("Should detect text with many control characters as image-only")
    void Should_ReturnTrue_When_ManyControlCharacters() throws Exception {
        // Text with non-printable characters (< 80% printable)
        String controlCharsText = "Some text\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000B\u000C\u000E\u000F";
        assertThat(invokeLooksImageOnly(controlCharsText)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "This is a normal text document with proper sentences and spacing. It contains enough text to be considered valid.",
        "Ceci est un document en français avec des accents éèêë et des caractères spéciaux. Il contient suffisamment de texte.",
        "Dies ist ein deutscher Text mit Umlauten äöü und ß. Er enthält genügend Text um als gültig zu gelten.",
        "This document contains numbers 123456 and punctuation! But it's still valid, readable text with good structure."
    })
    @DisplayName("Should not detect valid text as image-only")
    void Should_ReturnFalse_When_ValidText(String validText) throws Exception {
        assertThat(invokeLooksImageOnly(validText)).isFalse();
    }

    @Test
    @DisplayName("Should not detect technical text with some special chars as image-only")
    void Should_ReturnFalse_When_TechnicalTextWithSpecialChars() throws Exception {
        String technicalText = "The configuration file uses JSON format: {\"key\": \"value\", \"number\": 42}. " +
                              "This is normal for technical documentation and should not be skipped.";
        assertThat(invokeLooksImageOnly(technicalText)).isFalse();
    }

    @Test
    @DisplayName("Should not detect text with URLs and emails as image-only")
    void Should_ReturnFalse_When_TextWithUrlsAndEmails() throws Exception {
        String textWithLinks = "Contact us at support@example.com or visit our website https://example.com " +
                              "for more information about our products and services.";
        assertThat(invokeLooksImageOnly(textWithLinks)).isFalse();
    }

    @Test
    @DisplayName("Should not detect list with bullets as image-only")
    void Should_ReturnFalse_When_ListWithBullets() throws Exception {
        String listText = "Important points:\n• First item in the list\n• Second item with details\n" +
                         "• Third item with more information\nAll items are important.";
        assertThat(invokeLooksImageOnly(listText)).isFalse();
    }

    /**
     * Helper method to invoke private looksImageOnly method via reflection for testing.
     */
    private boolean invokeLooksImageOnly(String text) throws Exception {
        Method method = TikaAttachmentTextExtractorAdapter.class.getDeclaredMethod("looksImageOnly", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, text);
    }
}
