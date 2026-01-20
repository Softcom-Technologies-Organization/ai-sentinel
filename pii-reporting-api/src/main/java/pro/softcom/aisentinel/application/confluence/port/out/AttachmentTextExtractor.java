package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;

import java.util.Optional;

/**
 * Outbound port: text extraction from attachments.
 */
public interface AttachmentTextExtractor {
    Optional<String> extractText(AttachmentInfo info, byte[] bytes);
}
