package pro.softcom.sentinelle.application.pii.reporting.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service responsable de l'extraction de texte des pièces jointes Confluence.
 * Business intent: Télécharge les pièces jointes et extrait leur contenu textuel
 * pour permettre l'analyse PII ultérieure. Ne réalise PAS la détection PII elle-même,
 * cette responsabilité appartient au use case orchestrateur.
 */
@RequiredArgsConstructor
@Slf4j
public class AttachmentProcessor {

    private final ConfluenceAttachmentDownloader confluenceDownloadService;
    private final AttachmentTextExtractor attachmentTextExtractionService;

    /**
     * Extrait le texte de toutes les pièces jointes extractables d'une page.
     * 
     * @param pageId Identifiant de la page Confluence
     * @param attachments Liste des pièces jointes à traiter
     * @return Flux des textes extraits avec leurs métadonnées d'origine
     */
    public Flux<AttachmentTextExtracted> extractAttachmentsText(String pageId,
                                                                List<AttachmentInfo> attachments) {
        return Flux.fromIterable(attachments)
            .filter(this::isExtractableExtension)
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
