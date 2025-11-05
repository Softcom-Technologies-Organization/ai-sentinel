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
import pro.softcom.sentinelle.domain.confluence.AttachmentTypeFilter;

/**
 * WHAT: Attachment text extractor based on Apache Tika (programmatic API, no XML config).
 * Scope: initial support for PDFs and common office formats; image-only PDFs are skipped (no OCR yet).
 */
@Component
public class TikaAttachmentTextExtractorAdapter implements AttachmentTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TikaAttachmentTextExtractorAdapter.class);

    @Override
    public boolean supports(AttachmentInfo info) {
        return AttachmentTypeFilter.isExtractable(info);
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

    /**
     * Detects if extracted text appears to be from an image-only document (scanned PDF without proper OCR).
     * 
     * Business rules:
     * 1. Empty or blank text is considered image-only
     * 2. Text with less than 50 characters is likely garbage from OCR artifacts
     * 3. Text with less than 5% alphanumeric characters is likely corrupted
     * 4. Text without proper word spacing (no spaces) is likely OCR failure
     * 5. Text with excessive special characters suggests OCR artifacts
     * 
     * @param text The extracted text to analyze
     * @return true if the text appears to be from an image-only document
     */
    private static boolean looksImageOnly(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        
        // Rule 1: Very short text (< 50 chars) is likely OCR garbage
        if (text.length() < 50) {
            return true;
        }
        
        // Rule 2: Check alphanumeric character ratio
        long alphanumericCount = text.chars().filter(Character::isLetterOrDigit).count();
        double alphanumericRatio = alphanumericCount / (double) text.length();
        if (alphanumericRatio < 0.05) {
            return true;
        }
        
        // Rule 3: Check for proper word spacing (text should contain spaces)
        long spaceCount = text.chars().filter(ch -> ch == ' ').count();
        double spaceRatio = spaceCount / (double) text.length();
        // Natural text has roughly 12-20% spaces; less than 2% suggests OCR failure
        if (spaceRatio < 0.02 && text.length() > 100) {
            return true;
        }
        
        // Rule 4: Check ratio of printable ASCII vs special/control characters
        long printableCount = text.chars()
            .filter(ch -> (ch >= 32 && ch <= 126) || Character.isWhitespace(ch) || ch > 127)
            .count();
        double printableRatio = printableCount / (double) text.length();
        if (printableRatio < 0.80) {
            return true;
        }
        
        // Rule 5: Check for excessive punctuation/special chars (OCR artifacts)
        long specialCharCount = text.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();
        double specialCharRatio = specialCharCount / (double) text.length();
        // More than 40% special characters suggests corrupted OCR
        if (specialCharRatio > 0.40) {
            return true;
        }
        
        return false;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
