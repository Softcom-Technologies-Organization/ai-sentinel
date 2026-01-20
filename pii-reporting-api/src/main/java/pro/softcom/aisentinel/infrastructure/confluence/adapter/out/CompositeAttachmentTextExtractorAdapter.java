package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pro.softcom.aisentinel.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * WHAT: Composite implementation that delegates to the first supporting strategy.
 * Business value: allows plugging new extractors without changing callers (domain port remains stable).
 */
@Service
@Slf4j
public class CompositeAttachmentTextExtractorAdapter implements AttachmentTextExtractor {

    private final List<AttachmentTextExtractionStrategy> extractors;

    public CompositeAttachmentTextExtractorAdapter(List<AttachmentTextExtractionStrategy> extractors) {
        this.extractors = Objects.requireNonNullElseGet(extractors, List::of);
        log.info("Attachment text extraction initialized with {} extractor(s)", this.extractors.size());
    }

    @Override
    public Optional<String> extractText(AttachmentInfo info, byte[] bytes) {
        if (info == null || bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        for (AttachmentTextExtractionStrategy ex : extractors) {
            try {
                if (!ex.supports(info)) {
                    continue;
                }
                Optional<String> text = ex.extract(info, bytes);
                if (text.isPresent()) {
                    return text;
                }
            } catch (Exception e) {
                // Keep processing with next extractor while preserving stacktrace for diagnostics
                log.warn("Extractor {} failed for '{}'", ex.getClass().getSimpleName(), info.name(), e);
            }
        }
        return Optional.empty();
    }

}
