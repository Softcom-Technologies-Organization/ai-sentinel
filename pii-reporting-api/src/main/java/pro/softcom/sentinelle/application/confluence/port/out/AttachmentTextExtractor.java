package pro.softcom.sentinelle.application.confluence.port.out;

import java.util.Optional;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

/**
 * Outbound port: text extraction from attachments.
 */
public interface AttachmentTextExtractor {
    Optional<String> extractText(AttachmentInfo info, byte[] bytes);
}
