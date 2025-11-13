package pro.softcom.sentinelle.application.pii.reporting.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.AttachmentTypeFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service that extracts readable text from Confluence attachments.
 * Business purpose: downloads attachments and turns their content into text
 * so scans can analyze it later. It does not perform PII detection; that
 * responsibility belongs to the orchestrating use case.
 */
@RequiredArgsConstructor
@Slf4j
public class AttachmentProcessor {

    private final ConfluenceAttachmentDownloader confluenceDownloadService;
    private final AttachmentTextExtractor attachmentTextExtractionService;

    /**
     * Extracts readable text from all extractable attachments of a page.
     * Only supported file types are processed; others are ignored.
     *
     * @param pageId the Confluence page identifier
     * @param attachments the attachments to process
     * @return a Flux of extracted texts paired with their source attachment metadata
     */
    public Flux<AttachmentTextExtracted> extractAttachmentsText(String pageId,
                                                                List<AttachmentInfo> attachments) {
        return Flux.fromIterable(attachments)
            .filter(AttachmentTypeFilter::isExtractable)
            .concatMap(attachment -> extractAttachmentText(pageId, attachment));
    }

    private Flux<AttachmentTextExtracted> extractAttachmentText(String pageId,
                                                                AttachmentInfo attachment) {
        return downloadAttachment(pageId, attachment.name())
            .flatMapMany(bytes -> extractTextFromBytes(attachment, bytes))
            .map(text -> new AttachmentTextExtracted(attachment, text));
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
}
