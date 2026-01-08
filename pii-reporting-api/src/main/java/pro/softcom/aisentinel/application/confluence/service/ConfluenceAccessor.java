package pro.softcom.aisentinel.application.confluence.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpacePort;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

/**
 * Encapsulates Confluence data access operations.
 * Business purpose: Provides a unified interface for retrieving Confluence spaces, pages, and attachments,
 * abstracting the complexity of multiple client APIs.
 * Uses cached space data when available to improve stream startup performance.
 */
@RequiredArgsConstructor
public class ConfluenceAccessor {

    private final ConfluenceClient confluenceClient;
    private final ConfluenceAttachmentClient confluenceAttachmentClient;
    private final ConfluenceSpacePort confluenceSpacePort;

    public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
        return confluenceClient.getSpace(spaceKey);
    }

    /**
     * Retrieves all spaces using cache-first strategy via ConfluenceSpacePort.
     * Performance optimization: Uses cached data when available to avoid waiting for 
     * paginated HTTP calls to Confluence API before starting the scan stream.
     */
    public CompletableFuture<List<ConfluenceSpace>> getAllSpaces() {
        return confluenceSpacePort.getAllSpaces();
    }

    public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
        return confluenceClient.getAllPagesInSpace(spaceKey);
    }

    public CompletableFuture<List<AttachmentInfo>> getPageAttachments(String pageId) {
        return confluenceAttachmentClient.getPageAttachments(pageId);
    }
}