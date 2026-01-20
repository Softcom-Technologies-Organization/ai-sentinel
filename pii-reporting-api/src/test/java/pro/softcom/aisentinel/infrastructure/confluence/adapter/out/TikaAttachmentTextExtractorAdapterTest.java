package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.infrastructure.document.config.TextQualityThresholds;
import pro.softcom.aisentinel.infrastructure.document.validator.TextQualityValidator;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TikaAttachmentTextExtractorAdapter.
 * <p>
 * Tests the complete flow: Tika extraction + TextQualityValidator validation.
 */
@DisplayName("TikaAttachmentTextExtractorAdapter - Integration")
class TikaAttachmentTextExtractorAdapterTest {

    private TikaAttachmentTextExtractorAdapter adapter;

    @BeforeEach
    void setUp() {
        // Given
        TextQualityThresholds thresholds = new TextQualityThresholds();
        TextQualityValidator validator = new TextQualityValidator(thresholds);
        adapter = new TikaAttachmentTextExtractorAdapter(validator);
    }

    @Test
    @DisplayName("Should_SupportExtractableAttachments_When_PdfFile")
    void Should_SupportExtractableAttachments_When_PdfFile() {
        // Given
        AttachmentInfo pdfAttachment = createAttachment("document.pdf", "pdf");

        // When
        boolean supports = adapter.supports(pdfAttachment);

        // Then
        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Should_NotSupportNonExtractableAttachments_When_ImageFile")
    void Should_NotSupportNonExtractableAttachments_When_ImageFile() {
        // Given
        AttachmentInfo imageAttachment = createAttachment("photo.jpg", "jpg");

        // When
        boolean supports = adapter.supports(imageAttachment);

        // Then
        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_BytesNull")
    void Should_ReturnEmpty_When_BytesNull() {
        // Given
        AttachmentInfo attachment = createAttachment("doc.pdf", "pdf");

        // When
        Optional<String> result = adapter.extract(attachment, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_BytesEmpty")
    void Should_ReturnEmpty_When_BytesEmpty() {
        // Given
        AttachmentInfo attachment = createAttachment("doc.pdf", "pdf");
        byte[] emptyBytes = new byte[0];

        // When
        Optional<String> result = adapter.extract(attachment, emptyBytes);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ExtractText_When_PlainTextContent")
    void Should_ExtractText_When_PlainTextContent() {
        // Given
        AttachmentInfo attachment = createAttachment("document.txt", "txt");
        String validText = "This is a valid text document with enough content to pass validation. " +
                "It contains proper spacing and sufficient alphanumeric characters.";
        byte[] bytes = validText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<String> result = adapter.extract(attachment, bytes);

        // Then
        assertThat(result)
                .isPresent()
                .get()
                .asString()
                .contains("valid text document");
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_TextTooShort")
    void Should_ReturnEmpty_When_TextTooShort() {
        // Given
        AttachmentInfo attachment = createAttachment("short.txt", "txt");
        String shortText = "Too short";
        byte[] bytes = shortText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<String> result = adapter.extract(attachment, bytes);

        // Then - Validation should reject it
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_TextHasLowAlphanumericRatio")
    void Should_ReturnEmpty_When_TextHasLowAlphanumericRatio() {
        // Given
        AttachmentInfo attachment = createAttachment("corrupted.txt", "txt");
        String corruptedText = "!!!@@@###$$$%%%^^^&&&***((()))____----====++++||||\\\\\\///...";
        byte[] bytes = corruptedText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<String> result = adapter.extract(attachment, bytes);

        // Then - Validation should reject it
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_ExtractionFails")
    void Should_ReturnEmpty_When_ExtractionFails() {
        // Given
        AttachmentInfo attachment = createAttachment("invalid.pdf", "pdf");
        byte[] invalidBytes = "This is not a valid PDF".getBytes(StandardCharsets.UTF_8);

        // When
        Optional<String> result = adapter.extract(attachment, invalidBytes);

        // Then - Tika should fail to parse and return empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_BeAnnotatedAsComponent_When_ClassDefined")
    void Should_BeAnnotatedAsComponent_When_ClassDefined() {
        // Given & When & Then
        assertThat(TikaAttachmentTextExtractorAdapter.class)
                .hasAnnotation(org.springframework.stereotype.Component.class);
    }

    @Test
    @DisplayName("Should_ImplementStrategy_When_ClassDefined")
    void Should_ImplementStrategy_When_ClassDefined() {
        // Given & When & Then
        assertThat(AttachmentTextExtractionStrategy.class)
                .isAssignableFrom(TikaAttachmentTextExtractorAdapter.class);
    }

    private AttachmentInfo createAttachment(String name, String extension) {
        return new AttachmentInfo(name, extension, "application/octet-stream", "http://test");
    }
}
