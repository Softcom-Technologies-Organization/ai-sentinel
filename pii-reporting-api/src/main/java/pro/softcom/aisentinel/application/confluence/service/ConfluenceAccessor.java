package pro.softcom.aisentinel.application.confluence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Encapsulates Confluence data access operations.
 * Business purpose: Provides a unified interface for retrieving Confluence spaces, pages, and attachments,
 * abstracting the complexity of multiple client APIs.
 * Uses cached space data when available to improve stream startup performance.
 */
@RequiredArgsConstructor
@Slf4j
public class ConfluenceAccessor {

    private final ConfluenceClient confluenceClient;
    private final ConfluenceAttachmentClient confluenceAttachmentClient;
    private final ConfluenceSpaceRepository spaceRepository;

    public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
        return confluenceClient.getSpace(spaceKey);
    }

    /**
     * Retrieves all spaces using cache-first strategy.
     * Performance optimization: Uses cached data when available to avoid waiting for 
     * paginated HTTP calls to Confluence API before starting the scan stream.
     */
    public CompletableFuture<List<ConfluenceSpace>> getAllSpaces() {
        log.debug("Fetching Confluence spaces with cache-first strategy");
        
        List<ConfluenceSpace> cachedSpaces = spaceRepository.findAll();
        
        if (!cachedSpaces.isEmpty()) {
            log.debug("Returning {} cached spaces", cachedSpaces.size());
            return CompletableFuture.completedFuture(cachedSpaces);
        }
        
        log.debug("Cache miss - fetching spaces from Confluence API");
        return fetchAndCacheSpaces();
    }

    private CompletableFuture<List<ConfluenceSpace>> fetchAndCacheSpaces() {
        return confluenceClient.getAllSpaces()
            .thenApply(spaces -> {
                if (spaces != null && !spaces.isEmpty()) {
                    spaceRepository.saveAll(spaces);
                    log.info("Cached {} spaces from Confluence API", spaces.size());
                }
                return spaces;
            });
    }

    public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
        return confluenceClient.getAllPagesInSpace(spaceKey);
    }

    public CompletableFuture<List<AttachmentInfo>> getPageAttachments(String pageId) {
        return confluenceAttachmentClient.getPageAttachments(pageId);
    }
}