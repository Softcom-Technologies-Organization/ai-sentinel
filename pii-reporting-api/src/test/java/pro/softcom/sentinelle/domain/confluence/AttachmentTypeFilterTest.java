package pro.softcom.sentinelle.domain.confluence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttachmentTypeFilter")
class AttachmentTypeFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf", "txt",
            "csv", "odt", "ods", "odp", "html", "htm",
            // should be case-insensitive
            "PDF", "Docx", "TXT"
    })
    @DisplayName("Should return true when extension is supported")
    void Should_ReturnTrue_When_ExtensionIsSupported(String extension) {
        // Given
        AttachmentInfo attachment = createAttachment(extension);

        // When & Then
        assertThat(AttachmentTypeFilter.isExtractable(attachment)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"exe", "dll", "zip", "mp3", "jpg", "png"})
    @DisplayName("Should return false when extension is not supported")
    void Should_ReturnFalse_When_ExtensionNotSupported(String extension) {
        // Given
        AttachmentInfo attachment = createAttachment(extension);

        // When & Then
        assertThat(AttachmentTypeFilter.isExtractable(attachment)).isFalse();
    }

    @Test
    @DisplayName("Should return false when attachment or extension is null")
    void Should_ReturnFalse_When_AttachmentOrExtensionNull() {
        assertThat(AttachmentTypeFilter.isExtractable(null)).isFalse();
        assertThat(AttachmentTypeFilter.isExtractable(
                new AttachmentInfo("doc", null, "mime", "url")
        )).isFalse();
    }

    @Test
    @DisplayName("Should return all 15 supported extensions")
    void Should_ReturnAll15Extensions_When_GetSupportedExtensions() {
        // When
        Set<String> extensions = AttachmentTypeFilter.getSupportedExtensions();

        // Then
        assertThat(extensions)
                .hasSize(15)
                .containsExactlyInAnyOrder(
                        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf", "txt",
                        "csv", "odt", "ods", "odp", "html", "htm"
                );
    }

    @Test
    @DisplayName("Should return immutable set")
    void Should_ThrowException_When_TryingToModifySet() {
        // When
        Set<String> extensions = AttachmentTypeFilter.getSupportedExtensions();

        // Then
        assertThatThrownBy(() -> extensions.add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AttachmentInfo createAttachment(String extension) {
        return new AttachmentInfo("file." + extension, extension, "application/octet-stream", "http://test");
    }
}
