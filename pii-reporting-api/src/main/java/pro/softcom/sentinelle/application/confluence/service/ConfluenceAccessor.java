package pro.softcom.sentinelle.application.confluence.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

/**
 * Encapsulates Confluence data access operations.
 * Business purpose: Provides a unified interface for retrieving Confluence spaces, pages, and attachments,
 * abstracting the complexity of multiple client APIs.
 */
@RequiredArgsConstructor
public class ConfluenceAccessor {

    private final ConfluenceClient confluenceClient;
    private final ConfluenceAttachmentClient confluenceAttachmentClient;

    public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
        return confluenceClient.getSpace(spaceKey);
    }

    public CompletableFuture<List<ConfluenceSpace>> getAllSpaces() {
        return confluenceClient.getAllSpaces();
    }

    public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
        return confluenceClient.getAllPagesInSpace(spaceKey);
    }

    public CompletableFuture<List<AttachmentInfo>> getPageAttachments(String pageId) {
        return confluenceAttachmentClient.getPageAttachments(pageId);
    }
}
