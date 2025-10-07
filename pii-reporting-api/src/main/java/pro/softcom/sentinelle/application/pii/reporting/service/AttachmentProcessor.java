package pro.softcom.sentinelle.application.pii.reporting.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorSettings;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.scan.ScanProgress;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Processes Confluence attachments for PII detection.
 * Business intent: Handles attachment download, text extraction, and PII analysis workflow.
 */
@RequiredArgsConstructor
@Slf4j
public class AttachmentProcessor {

    private final ConfluenceAttachmentDownloader confluenceDownloadService;
    private final AttachmentTextExtractor attachmentTextExtractionService;
    private final PiiDetectorClient piiDetectorClient;
    private final PiiDetectorSettings piiSettings;
    private final ScanEventFactory eventFactory;
    private final ScanProgressCalculator progressCalculator;

    /**
     * Processes all attachments for a page and returns scan result events.
     */
    public Flux<ScanResult> processAttachments(String scanId, String spaceKey, ConfluencePage page,
                                              List<AttachmentInfo> attachments, ScanProgress scanProgress) {
        return Flux.fromIterable(attachments)
            .filter(this::isExtractableExtension)
            .concatMap(attachment -> processAttachment(scanId, spaceKey, page, attachment,
                                                       scanProgress));
    }

    /**
     * Processes a single attachment through the complete workflow.
     */
    private Flux<ScanResult> processAttachment(String scanId, String spaceKey, ConfluencePage page,
                                              AttachmentInfo attachment, ScanProgress scanProgress) {
        return downloadAttachment(page.id(), attachment.name())
            .flatMapMany(bytes -> extractAndAnalyze(scanId, spaceKey, page, attachment, bytes,
                                                    scanProgress));
    }

    /**
     * Downloads attachment content.
     */
    private Mono<byte[]> downloadAttachment(String pageId, String attachmentName) {
        return Mono.fromFuture(
                confluenceDownloadService.downloadAttachmentContent(pageId, attachmentName))
            .flatMap(optional -> optional.map(Mono::just).orElse(Mono.empty()));
    }

    /**
     * Extracts text from attachment and performs PII analysis.
     */
    private Flux<ScanResult> extractAndAnalyze(String scanId, String spaceKey, ConfluencePage page,
                                              AttachmentInfo attachment, byte[] bytes,
                                              ScanProgress scanProgress) {
        return Mono.fromCallable(() -> extractText(attachment, bytes))
            .flatMapMany(textOptional -> textOptional
                .map(text -> analyzeAndCreateEvent(scanId, spaceKey, page, attachment, text,
                                                   scanProgress))
                .orElse(Flux.empty()));
    }

    /**
     * Extracts text from attachment bytes.
     */
    private Optional<String> extractText(AttachmentInfo attachment, byte[] bytes) {
        return attachmentTextExtractionService.extractText(attachment, bytes);
    }

    /**
     * Analyzes text for PII and creates scan result event.
     */
    private Flux<ScanResult> analyzeAndCreateEvent(String scanId, String spaceKey,
                                                   ConfluencePage page, AttachmentInfo attachment,
                                                   String text, ScanProgress scanProgress) {
        ContentPiiDetection detection = detectPii(text);
        double progress = progressCalculator.calculateProgress(
            scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
            scanProgress.originalTotal());

        ScanResult event = eventFactory.createAttachmentItemEvent(
            scanId, spaceKey, page, attachment, text, detection, progress);

        return Flux.just(event);
    }

    /**
     * Performs PII detection on content.
     */
    private ContentPiiDetection detectPii(String content) {
        String safeContent = content != null ? content : "";
        return piiDetectorClient.analyzeContent(safeContent, piiSettings.defaultThreshold());
    }

    /**
     * Checks if attachment extension is supported for text extraction.
     */
    private boolean isExtractableExtension(AttachmentInfo attachment) {
        String extension = attachment.extension();
        if (extension == null || extension.isBlank()) {
            return false;
        }
        String lowercaseExtension = extension.toLowerCase();
        return switch (lowercaseExtension) {
            case "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf", "odt", "ods", "odp",
                 "txt", "csv", "html", "htm" -> true;
            default -> false;
        };
    }


}
