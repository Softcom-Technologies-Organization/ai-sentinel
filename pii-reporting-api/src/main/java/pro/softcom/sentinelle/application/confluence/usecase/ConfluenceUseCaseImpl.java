package pro.softcom.sentinelle.application.confluence.usecase;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

@RequiredArgsConstructor
@Slf4j
public class ConfluenceUseCaseImpl implements ConfluenceUseCase {

  private final ConfluenceClient confluenceClient;
  private final ConfluenceSpaceRepository spaceRepository;

  @Override
  public CompletableFuture<Boolean> testConnection() {
    return confluenceClient.testConnection();
  }

  @Override
  public CompletableFuture<Optional<ConfluencePage>> getPage(String pageId) {
    return confluenceClient.getPage(pageId);
  }

  @Override
  public CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query) {
    return confluenceClient.searchPages(spaceKey, query);
  }

  @Override
  public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
    return confluenceClient.getSpace(spaceKey);
  }

  @Override
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

  @Override
  public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
    return confluenceClient.getAllPagesInSpace(spaceKey);
  }
}
