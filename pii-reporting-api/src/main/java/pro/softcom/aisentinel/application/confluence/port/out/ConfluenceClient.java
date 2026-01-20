package pro.softcom.aisentinel.application.confluence.port.out;

import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ModifiedPageInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Confluence client (outbound domain port).
 * Describes the capabilities needed by the domain to interact with Confluence.
 */
public interface ConfluenceClient {

    /**
     * Retrieves a Confluence page by its ID
     * @param pageId the page identifier
     * @return the page if it exists
     */
    CompletableFuture<Optional<ConfluencePage>> getPage(String pageId);

    /**
     * Searches for pages in a space
     * @param spaceKey the space key
     * @param query the search query
     * @return list of matching pages
     */
    CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query);

    /**
     * Updates an existing page
     * @param page the page with modifications
     * @return the updated page
     */
    CompletableFuture<ConfluencePage> updatePage(ConfluencePage page);

    /**
     * Retrieves space information
     * @param spaceKey the space key
     * @return the space information
     */
    CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey);

    /**
     * Retrieves space information with permissions/data owners.
     * This method expands the permissions field to load data owners.
     *
     * @param spaceKey the space key
     * @return the space information with loaded data owners
     */
    CompletableFuture<Optional<ConfluenceSpace>> getSpaceWithPermissions(String spaceKey);

    /**
     * Retrieves space information par son ID
     * @param spaceId the space identifier
     * @return the space information
     */
    CompletableFuture<Optional<ConfluenceSpace>> getSpaceById(String spaceId);

    /**
     * Tests the connection to Confluence
     * @return true if the connection is established
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Retrieves all Confluence spaces
     * @return list of spaces
     */
    CompletableFuture<List<ConfluenceSpace>> getAllSpaces();

    /**
     * Retrieves all pages in a space
     * @param spaceKey the space key
     * @return list of pages in the space
     */
    CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey);

    /**
     * Retrieves all pages modified in a space since a specific date.
     * Uses CQL Content Search API to find pages modified after the given date.
     * 
     * Business purpose: Provides detailed information about which pages were modified
     * to populate the dashboard with specific update counts and page titles.
     * 
     * @param spaceKey the space key
     * @param sinceDate the date from which to search for modifications (typically last scan date)
     * @return list of modified pages with their information, or empty list if no modifications found
     */
    CompletableFuture<List<ModifiedPageInfo>> getModifiedPagesSince(
        String spaceKey, 
        Instant sinceDate
    );

    CompletableFuture<List<ModifiedAttachmentInfo>> getModifiedAttachmentsSince(
        String spaceKey,
        Instant sinceDate
    );
}
