package pro.softcom.aisentinel.application.confluence.port.in;

import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Input port exposing read-only Confluence space and page operations to the application layer.
 *
 * <p>All operations are asynchronous ({@link CompletableFuture}) to support non-blocking scan
 * workflows. Implementations are expected to delegate to a Confluence REST client.
 */
public interface ConfluenceSpacePort {

  /** Probes the configured Confluence instance for reachability and authentication. */
  CompletableFuture<Boolean> testConnection();

  /** Fetches a single page by its identifier, empty when absent. */
  CompletableFuture<Optional<ConfluencePage>> getPage(String pageId);

  /** Searches pages within a space matching the given free-text query. */
  CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query);

  /** Fetches a single space by its key, empty when absent. */
  CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey);

  /** Lists all spaces the authenticated user can access. */
  CompletableFuture<List<ConfluenceSpace>> getAllSpaces();

  /** Lists all pages within the given space. */
  CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey);
}

