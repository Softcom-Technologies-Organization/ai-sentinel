package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

@ExtendWith(MockitoExtension.class)
class TikaAttachmentTextExtractorAdapterTest {

    private final TikaAttachmentTextExtractorAdapter extractor = new TikaAttachmentTextExtractorAdapter();

    @Test
    void supports_should_handle_null_and_known_extensions() {
        // Arrange
        AttachmentInfo nullInfo = null;
        AttachmentInfo nullExt = new AttachmentInfo("file", null, "application/octet-stream", "url");
        AttachmentInfo pdf = new AttachmentInfo("f.pdf", "pdf", "application/pdf", "url");
        AttachmentInfo docx = new AttachmentInfo("f.DOCX", "DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "url");
        AttachmentInfo jpg = new AttachmentInfo("i.jpg", "jpg", "image/jpeg", "url");

        // Act & Assert
        assertSoftly(soft -> {
            soft.assertThat(extractor.supports(nullInfo)).as("null info").isFalse();
            soft.assertThat(extractor.supports(nullExt)).as("null extension").isFalse();
            soft.assertThat(extractor.supports(pdf)).as("pdf supported").isTrue();
            soft.assertThat(extractor.supports(docx)).as("case-insensitive").isTrue();
            soft.assertThat(extractor.supports(jpg)).as("unsupported jpg").isFalse();
        });
    }

    @Test
    void extract_should_return_empty_on_null_or_empty_bytes() {
        AttachmentInfo txt = new AttachmentInfo("a.txt", "txt", "text/plain", "url");

        assertSoftly(soft -> {
            soft.assertThat(extractor.extract(txt, null)).as("null bytes").isEmpty();
            soft.assertThat(extractor.extract(txt, new byte[0])).as("empty bytes").isEmpty();
        });
    }

    @Test
    void extract_should_trim_and_return_text_for_plain_text_input() {
        AttachmentInfo txt = new AttachmentInfo("a.txt", "txt", "text/plain", "url");
        byte[] bytes = "  Hello \nWorld  ".getBytes(StandardCharsets.UTF_8);

        Optional<String> out = extractor.extract(txt, bytes);

        // Expect trimming of leading/trailing spaces but preservation of inner newline
        assertThat(out).isPresent();
        assertThat(out.orElseThrow()).isEqualTo("Hello \nWorld");
    }

    @Test
    void extract_should_apply_image_only_heuristic_and_skip_low_alnum_text() {
        // With heuristic enabled: content with very low alphanumeric ratio should be skipped
        byte[] lowAlnum = " . , !  ".getBytes(StandardCharsets.UTF_8);

        AttachmentInfo pdf = new AttachmentInfo("img.pdf", "pdf", "application/pdf", "url");
        AttachmentInfo txt = new AttachmentInfo("symbols.txt", "txt", "text/plain", "url");

        Optional<String> pdfOut = extractor.extract(pdf, lowAlnum);
        Optional<String> txtOut = extractor.extract(txt, lowAlnum);

        // For both PDF and TXT, the heuristic should skip low-alphanumeric content
        assertThat(pdfOut).isEmpty();
        assertThat(txtOut).isEmpty();
    }

    @Test
    void extract_should_return_empty_when_text_is_blank_after_trim() {
        AttachmentInfo pdf = new AttachmentInfo("blank.pdf", "pdf", "application/pdf", "url");
        byte[] blanks = "    ".getBytes(StandardCharsets.UTF_8);

        Optional<String> out = extractor.extract(pdf, blanks);
        assertThat(out).isEmpty();
    }
}
