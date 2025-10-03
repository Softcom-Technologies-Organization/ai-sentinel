package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

/**
 * WHAT: Attachment text extractor based on Apache Tika (programmatic API, no XML config).
 * Scope: initial support for PDFs and common office formats; image-only PDFs are skipped (no OCR yet).
 */
@Component
public class TikaAttachmentTextExtractorAdapter implements AttachmentTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TikaAttachmentTextExtractorAdapter.class);

    @Override
    public boolean supports(AttachmentInfo info) {
        if (info == null) return false;
        String ext = info.extension() != null ? info.extension().toLowerCase() : "";
        // Broader support via Tika; start with common document types
        return switch (ext) {
            case "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf", "odt", "ods", "odp", "txt", "csv" -> true;
            default -> false;
        };
    }

    @Override
    public Optional<String> extract(AttachmentInfo info, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(in, handler, metadata, context);
            String text = safeTrim(handler.toString());

            // If content looks like image-only (e.g., scanned PDF without OCR), skip indexing
            if (looksImageOnly(text)) {
                logger.debug("[ATTACHMENT_TEXT][TIKA][SKIP_IMAGE_ONLY] name='{}'", info != null ? info.name() : "?");
                return Optional.empty();
            }

            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            logger.warn("[ATTACHMENT_TEXT][TIKA][ERROR] name='{}' - {}", info != null ? info.name() : "?", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean looksImageOnly(String text) {
        if (text == null || text.isBlank()) return true;
        int len = text.length();
        long alnum = text.chars().filter(Character::isLetterOrDigit).count();
        double ratio = alnum / (double) len;
        return ratio < 0.05; // heuristic threshold
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
