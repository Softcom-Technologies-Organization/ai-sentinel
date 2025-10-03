package pro.softcom.sentinelle.application.confluence.usecase;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

@RequiredArgsConstructor
public class ConfluenceUseCaseImpl implements ConfluenceUseCase {

  private final ConfluenceClient confluenceClient;

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
  public CompletableFuture<Optional<ConfluencePage>> updatePage(String pageId, String title, String content, List<String> labels) {
    return doUpdate(pageId, title, content, labels);
  }

  private CompletableFuture<Optional<ConfluencePage>> doUpdate(String pageId, String title, String content, List<String> labels) {
    if (pageId == null || pageId.isBlank()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    // Récupère l'existant pour préserver les métadonnées/version et champs non modifiés
    return confluenceClient.getPage(pageId).thenCompose(optExisting -> {
      if (optExisting.isEmpty()) {
        return CompletableFuture.completedFuture(Optional.empty());
      }
      var existing = optExisting.get();
      var updated = new ConfluencePage(
          existing.id(),
          title != null ? title : existing.title(),
          existing.spaceKey(),
          content != null ? new ConfluencePage.HtmlContent(content) : existing.content(),
          existing.metadata(),
          labels != null ? labels : existing.labels(),
          existing.customProperties()
      );
      return confluenceClient.updatePage(updated).thenApply(Optional::of);
    });
  }

  @Override
  public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
    return confluenceClient.getSpace(spaceKey);
  }

  @Override
  public CompletableFuture<Optional<ConfluenceSpace>> getSpaceById(String spaceId) {
    return confluenceClient.getSpaceById(spaceId);
  }

  @Override
  public CompletableFuture<List<ConfluenceSpace>> getAllSpaces() {
    return confluenceClient.getAllSpaces();
  }

  @Override
  public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
    return confluenceClient.getAllPagesInSpace(spaceKey);
  }
}
