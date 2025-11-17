package pro.softcom.sentinelle.application.confluence.port.in;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;

public interface ConfluenceSpacePort {
  CompletableFuture<Boolean> testConnection();
  CompletableFuture<Optional<ConfluencePage>> getPage(String pageId);
  CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query);
  CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey);
  CompletableFuture<List<ConfluenceSpace>> getAllSpaces();
  CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey);
}

