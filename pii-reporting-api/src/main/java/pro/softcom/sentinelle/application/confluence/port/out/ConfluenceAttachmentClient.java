package pro.softcom.sentinelle.application.confluence.port.out;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;

/**
 * Outbound port to retrieve Confluence attachment metadata.
 */
public interface ConfluenceAttachmentClient {

    CompletableFuture<List<AttachmentInfo>> getPageAttachments(String pageId);
}
