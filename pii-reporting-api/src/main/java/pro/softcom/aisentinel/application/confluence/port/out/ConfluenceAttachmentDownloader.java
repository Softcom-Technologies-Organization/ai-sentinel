package pro.softcom.aisentinel.application.confluence.port.out;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Outbound port to download binary content of Confluence attachments.
 */
public interface ConfluenceAttachmentDownloader {

    CompletableFuture<Optional<byte[]>> downloadAttachmentContent(String pageId, String attachmentTitle);
}
