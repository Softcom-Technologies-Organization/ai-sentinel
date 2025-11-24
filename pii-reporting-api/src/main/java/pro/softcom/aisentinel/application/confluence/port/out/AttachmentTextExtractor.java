package pro.softcom.aisentinel.application.confluence.port.out;

import java.util.Optional;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;

/**
 * Outbound port: text extraction from attachments.
 */
public interface AttachmentTextExtractor {
    Optional<String> extractText(AttachmentInfo info, byte[] bytes);
}
