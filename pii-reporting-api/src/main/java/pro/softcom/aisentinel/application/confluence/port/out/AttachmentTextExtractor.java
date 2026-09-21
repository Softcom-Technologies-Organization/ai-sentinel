package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;

import java.util.Optional;

/**
 * Outbound port: text extraction from attachments.
 */
public interface AttachmentTextExtractor {
    Optional<ExtractedContent> extractText(AttachmentInfo info, byte[] bytes);
}
