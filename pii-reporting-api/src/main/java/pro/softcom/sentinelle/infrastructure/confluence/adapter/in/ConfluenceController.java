package pro.softcom.sentinelle.infrastructure.confluence.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto.ConfluencePageDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto.ConfluenceSearchResponseDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.dto.ConfluenceSpaceDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.in.mapper.ConfluenceApiMapper;

/**
 * REST Controller for Confluence operations.
 * Exposes endpoints to interact with Confluence.
 */
@RestController
@RequestMapping("/api/v1/confluence")
@Tag(name = "Confluence", description = "Confluence operations")
@RequiredArgsConstructor
@Slf4j
public class ConfluenceController {

    private final ConfluenceUseCase confluenceUseCase;

    @GetMapping("/health")
    @Operation(summary = "Check Confluence connection")
    @ApiResponse(responseCode = "200", description = "Connection established")
    @ApiResponse(responseCode = "503", description = "Confluence not accessible")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceHealthCheckResponse>> checkHealth() {
        return confluenceUseCase.testConnection()
                .thenApply(isConnected -> {
                    var response = new ConfluenceHealthCheckResponse(
                            Boolean.TRUE.equals(isConnected) ? "UP" : "DOWN",
                            Boolean.TRUE.equals(isConnected) ? "Connection to Confluence established" : "Confluence not accessible"
                    );
                    return Boolean.TRUE.equals(isConnected)
                            ? ResponseEntity.ok(response)
                            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                });
    }

    @GetMapping("/pages/{pageId}")
    @Operation(summary = "Retrieve a page by its ID")
    @ApiResponse(responseCode = "200", description = "Page found")
    @ApiResponse(responseCode = "404", description = "Page not found")
    public CompletableFuture<ResponseEntity<@NonNull ConfluencePageDto>> getPage(
            @Parameter(description = "Page ID") @PathVariable String pageId) {

        log.info("GET request /pages/{}", pageId);

        return confluenceUseCase.getPage(pageId)
                .thenApply(optionalPage ->
                        optionalPage
                                .map(ConfluenceApiMapper::toDto)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build())
                );
    }

    @GetMapping("/spaces/{spaceKey}/search")
    @Operation(summary = "Search pages in a space")
    @ApiResponse(responseCode = "200", description = "Search results")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceSearchResponseDto>> searchPages(
            @Parameter(description = "Space key") @PathVariable String spaceKey,
            @Parameter(description = "Search query") @RequestParam String query,
            @Parameter(description = "Result limit") @RequestParam(defaultValue = "20") int limit) {

        log.info("Search in space {} : {}", spaceKey, query);

        return confluenceUseCase.searchPages(spaceKey, query)
                .thenApply(pages -> {
                    var limitedPages = pages == null ? List.<ConfluencePage>of() : pages.stream().limit(limit).toList();
                    var dtoPages = ConfluenceApiMapper.toDtoPages(limitedPages);
                    var response = new ConfluenceSearchResponseDto(dtoPages, dtoPages.size(), query);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping("/spaces/{spaceKey}")
    @Operation(summary = "Retrieve space information")
    @ApiResponse(responseCode = "200", description = "Space found")
    @ApiResponse(responseCode = "404", description = "Space not found")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceSpaceDto>> getSpace(
            @Parameter(description = "Space key") @PathVariable String spaceKey) {

        log.info("GET request /spaces/{}", spaceKey);

        return confluenceUseCase.getSpace(spaceKey)
                .thenApply(optionalSpace ->
                        optionalSpace
                                .map(ConfluenceApiMapper::toDto)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build())
                );
    }

    @GetMapping("/spaces/{spaceKey}/pages")
    @Operation(summary = "Retrieve all pages in a space")
    @ApiResponse(responseCode = "200", description = "List of pages")
    @ApiResponse(responseCode = "404", description = "Space not found")
    public CompletableFuture<ResponseEntity<@NonNull List<ConfluencePageDto>>> getAllPagesInSpace(
            @Parameter(description = "Space key") @PathVariable String spaceKey) {

        log.info("GET request /spaces/{}/pages", spaceKey);

        return confluenceUseCase.getSpace(spaceKey)
                .thenCompose(optionalSpace -> {
                    if (optionalSpace.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.notFound().build()
                        );
                    }

                    return confluenceUseCase.getAllPagesInSpace(spaceKey)
                            .thenApply(pages -> ResponseEntity.ok(ConfluenceApiMapper.toDtoPages(pages)));
                });
    }

    @GetMapping("/spaces")
    @Operation(summary = "Retrieve all Confluence spaces")
    @ApiResponse(responseCode = "200", description = "List of spaces")
    public CompletableFuture<ResponseEntity<@NonNull List<ConfluenceSpaceDto>>> getAllSpaces() {
        log.info("GET request /spaces");

        return confluenceUseCase.getAllSpaces()
            .thenApply(spaces -> ResponseEntity.ok(ConfluenceApiMapper.toDtoSpaces(spaces)))
            .exceptionally(ex -> {
                log.error("Error retrieving spaces", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    public record ConfluenceHealthCheckResponse(String status, String message) { }
}
