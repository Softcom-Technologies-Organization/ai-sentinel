package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import pro.softcom.aisentinel.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;

import java.util.Optional;

/**
 * Adapter-out internal strategy for attachment text extraction.
 * Purpose: allow multiple technology-specific extractors (e.g., Tika) to plug into a composite.
 * This is not a domain port; the domain-facing port is {@link AttachmentTextExtractor}.
 */
public interface AttachmentTextExtractionStrategy {
    /**
     * Indicates whether this extractor supports the given attachment (by extension or other criteria).
     */
    boolean supports(AttachmentInfo info);

    /**
     * Extracts text from the given attachment bytes.
     * Returns Optional.empty() when no text could be extracted or in case of errors.
     */
    Optional<ExtractedContent> extract(AttachmentInfo info, byte[] bytes);
}
