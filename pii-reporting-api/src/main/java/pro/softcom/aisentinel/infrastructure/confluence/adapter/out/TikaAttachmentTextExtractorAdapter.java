package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.AttachmentTypeFilter;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;
import pro.softcom.aisentinel.infrastructure.document.validator.TextQualityValidator;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * WHAT: Attachment text extractor based on Apache Tika (programmatic API, no XML config).
 * Scope: initial support for PDFs and common office formats; image-only PDFs are skipped (no OCR yet).
 */
@Component
public class TikaAttachmentTextExtractorAdapter implements AttachmentTextExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TikaAttachmentTextExtractorAdapter.class);
    private static final Set<String> TABLE_FORMATS = Set.of("xls", "xlsx", "csv", "ods", "doc", "docx", "odt", "html", "htm");
    private static final Set<String> INFERRED_HEADER_FORMATS = Set.of("xls", "xlsx", "csv", "ods");
    private static final int MAX_STRUCTURED_UNITS = 2_000_000;

    private final TextQualityValidator textQualityValidator;

    public TikaAttachmentTextExtractorAdapter(TextQualityValidator textQualityValidator) {
        this.textQualityValidator = textQualityValidator;
    }

    @Override
    public boolean supports(AttachmentInfo info) {
        return AttachmentTypeFilter.isExtractable(info);
    }

    @Override
    public Optional<ExtractedContent> extract(AttachmentInfo info, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Optional.empty();
        if (info != null && info.extension() != null
                && TABLE_FORMATS.contains(info.extension().strip().toLowerCase(Locale.ROOT))) {
            Optional<ExtractedContent> structured = extractStructured(info, bytes);
            if (structured.isPresent()) {
                return validate(structured.get(), info);
            }
        }
        return extractPlain(info, bytes);
    }

    private Optional<ExtractedContent> extractPlain(AttachmentInfo info, byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(in, handler, metadata, context);
            String text = StringUtils.trim(handler.toString());

            return validate(ExtractedContent.identity(text), info);
        } catch (Exception e) {
            logger.warn("[ATTACHMENT_TEXT][TIKA][ERROR] name='{}' - {}", info != null ? info.name() : "?", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ExtractedContent> extractStructured(AttachmentInfo info, byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Document document = parseDocument(info, input);
            if ("csv".equalsIgnoreCase(info.extension().strip()) && document.select("table").isEmpty()) {
                document = CsvAttachmentTableParser.parse(document.body().wholeText().strip()).orElse(document);
            }
            if (INFERRED_HEADER_FORMATS.contains(info.extension().strip().toLowerCase(Locale.ROOT))) {
                MediaType type = info.mimeType() == null ? null : MediaType.parse(info.mimeType());
                String declaration = "csv".equalsIgnoreCase(info.extension().strip()) && type != null
                        ? type.getParameters().getOrDefault("header", "") : "";
                TabularAttachmentHeaders.mark(document, declaration);
            }
            return Optional.of(new HtmlContentParser().extractContent(document.outerHtml()));
        } catch (Exception exception) {
            logger.warn("[ATTACHMENT_TEXT][TIKA][TABLE_FALLBACK] name='{}' - {}", info.name(), exception.getMessage());
            return Optional.empty();
        }
    }

    private Document parseDocument(AttachmentInfo info, ByteArrayInputStream input) throws Exception {
        if (Set.of("xls", "xlsx").contains(info.extension().strip().toLowerCase(Locale.ROOT))) {
            return SpreadsheetAttachmentTableParser.parse(input);
        }
        if (Set.of("html", "htm").contains(info.extension().strip().toLowerCase(Locale.ROOT))) {
            if (input.available() > MAX_STRUCTURED_UNITS) {
                throw new SAXException("Structured extraction exceeds the limit; retry as plain text");
            }
            return Jsoup.parse(input, null, "");
        }
        LimitedXhtmlHandler handler = new LimitedXhtmlHandler();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, info.name());
        new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
        return Jsoup.parse(handler.toString());
    }

    private Optional<ExtractedContent> validate(ExtractedContent content, AttachmentInfo info) {
        if (textQualityValidator.isImageOnlyDocument(content.contextText())) {
            logger.debug("[ATTACHMENT_TEXT][TIKA][SKIP_IMAGE_ONLY] name='{}'", info != null ? info.name() : "?");
            return Optional.empty();
        }
        return Optional.of(content);
    }

    private static final class LimitedXhtmlHandler extends ToXMLContentHandler {

        private long units;

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
            count(32L + name.length());
            for (int index = 0; index < attributes.getLength(); index++) {
                count((long) attributes.getQName(index).length() + attributes.getValue(index).length());
            }
            super.startElement(uri, localName, name, attributes);
        }

        @Override
        public void characters(char[] characters, int start, int length) throws SAXException {
            count(length);
            super.characters(characters, start, length);
        }

        private void count(long additionalUnits) throws SAXException {
            units += additionalUnits;
            if (units > MAX_STRUCTURED_UNITS) {
                throw new SAXException("Structured extraction exceeds the limit; retry as plain text");
            }
        }
    }
}
