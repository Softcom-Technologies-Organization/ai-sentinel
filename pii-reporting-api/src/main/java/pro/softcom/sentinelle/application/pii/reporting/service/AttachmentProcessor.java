package pro.softcom.sentinelle.application.pii.reporting.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import pro.softcom.sentinelle.domain.pii.scan.ScanProgress;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public class AttachmentProcessor {

    private final ConfluenceAttachmentDownloader confluenceDownloadService;
    private final AttachmentTextExtractor attachmentTextExtractionService;
    private final PiiDetectorClient piiDetectorClient;
    private final ScanEventFactory eventFactory;
    private final ScanProgressCalculator progressCalculator;

    public Flux<ScanResult> processAttachments(String scanId, String spaceKey, ConfluencePage page,
                                              List<AttachmentInfo> attachments, ScanProgress scanProgress) {
        return Flux.fromIterable(attachments)
            .filter(this::isExtractableExtension)
            .concatMap(attachment -> processAttachment(scanId, spaceKey, page, attachment,
                                                       scanProgress));
    }

    private Flux<ScanResult> processAttachment(String scanId, String spaceKey, ConfluencePage page,
                                              AttachmentInfo attachment, ScanProgress scanProgress) {
        return downloadAttachment(page.id(), attachment.name())
            .flatMapMany(bytes -> extractTextFromBytes(attachment, bytes))
            .flatMap(text -> analyzeText(scanId, spaceKey, page, attachment, text, scanProgress));
    }

    private Mono<byte[]> downloadAttachment(String pageId, String attachmentName) {
        return Mono.fromFuture(
                confluenceDownloadService.downloadAttachmentContent(pageId, attachmentName))
            .flatMap(optional -> optional.map(Mono::just).orElse(Mono.empty()));
    }

    private Mono<String> extractTextFromBytes(AttachmentInfo attachment, byte[] bytes) {
        return Mono.fromCallable(
                () -> attachmentTextExtractionService.extractText(attachment, bytes))
            .flatMap(textOptional -> textOptional.map(Mono::just).orElse(Mono.empty()));
    }

    private Mono<ScanResult> analyzeText(String scanId, String spaceKey, ConfluencePage page,
                                         AttachmentInfo attachment, String text,
                                         ScanProgress scanProgress) {
        ContentPiiDetection detection = detectPii(text);
        double progress = progressCalculator.calculateProgress(
            scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
            scanProgress.originalTotal());

        ScanResult event = eventFactory.createAttachmentItemEvent(
            scanId, spaceKey, page, attachment, text, detection, progress);

        return Mono.just(event);
    }

    private ContentPiiDetection detectPii(String content) {
        String safeContent = content != null ? content : "";
        return piiDetectorClient.analyzeContent(safeContent);
    }

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
