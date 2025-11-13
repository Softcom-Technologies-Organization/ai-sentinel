package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.AttachmentTypeFilter;
import pro.softcom.sentinelle.infrastructure.document.validator.TextQualityValidator;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * WHAT: Attachment text extractor based on Apache Tika (programmatic API, no XML config).
 * Scope: initial support for PDFs and common office formats; image-only PDFs are skipped (no OCR yet).
 */
@Component
public class TikaAttachmentTextExtractorAdapter implements AttachmentTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TikaAttachmentTextExtractorAdapter.class);

    private final TextQualityValidator textQualityValidator;

    public TikaAttachmentTextExtractorAdapter(TextQualityValidator textQualityValidator) {
        this.textQualityValidator = textQualityValidator;
    }

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
            String text = StringUtils.trim(handler.toString());

            // If content looks like image-only (e.g., scanned PDF without OCR), skip indexing
            if (textQualityValidator.isImageOnlyDocument(text)) {
                logger.debug("[ATTACHMENT_TEXT][TIKA][SKIP_IMAGE_ONLY] name='{}'", info != null ? info.name() : "?");
                return Optional.empty();
            }

            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            logger.warn("[ATTACHMENT_TEXT][TIKA][ERROR] name='{}' - {}", info != null ? info.name() : "?", e.getMessage());
            return Optional.empty();
        }
    }

}
