package pro.softcom.aisentinel.application.confluence.port.in;

import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ConfluenceSpacePort {
  CompletableFuture<Boolean> testConnection();
  CompletableFuture<Optional<ConfluencePage>> getPage(String pageId);
  CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query);
  CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey);
  CompletableFuture<List<ConfluenceSpace>> getAllSpaces();
  CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey);
}

