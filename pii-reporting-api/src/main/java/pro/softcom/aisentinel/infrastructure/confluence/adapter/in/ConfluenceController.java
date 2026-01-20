package pro.softcom.aisentinel.infrastructure.confluence.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpacePort;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpaceUpdateInfoPort;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.SpaceUpdateInfo;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluencePageDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluenceSearchResponseDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluenceSpaceDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.SpaceUpdateInfoDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.mapper.ConfluenceApiMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    private final ConfluenceSpacePort confluenceSpacePort;
    private final ConfluenceSpaceUpdateInfoPort confluenceSpaceUpdateInfoPort;

    @GetMapping("/health")
    @Operation(summary = "Check Confluence connection")
    @ApiResponse(responseCode = "200", description = "Connection established")
    @ApiResponse(responseCode = "503", description = "Confluence not accessible")
    public CompletableFuture<ResponseEntity<@NonNull ConfluenceHealthCheckResponse>> checkHealth() {
        return confluenceSpacePort.testConnection()
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

        return confluenceSpacePort.getPage(pageId)
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

        return confluenceSpacePort.searchPages(spaceKey, query)
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

        return confluenceSpacePort.getSpace(spaceKey)
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

        return confluenceSpacePort.getSpace(spaceKey)
                .thenCompose(optionalSpace -> {
                    if (optionalSpace.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                ResponseEntity.notFound().build()
                        );
                    }

                    return confluenceSpacePort.getAllPagesInSpace(spaceKey)
                            .thenApply(pages -> ResponseEntity.ok(ConfluenceApiMapper.toDtoPages(pages)));
                });
    }

    @GetMapping("/spaces")
    @Operation(summary = "Retrieve all Confluence spaces")
    @ApiResponse(responseCode = "200", description = "List of spaces")
    public CompletableFuture<ResponseEntity<@NonNull List<ConfluenceSpaceDto>>> getAllSpaces() {
        log.info("GET request /spaces");

        return confluenceSpacePort.getAllSpaces()
            .thenApply(spaces -> ResponseEntity.ok(ConfluenceApiMapper.toDtoSpaces(spaces)))
            .exceptionally(ex -> {
                log.error("Error retrieving spaces", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GetMapping("/spaces/update-info")
    @Operation(summary = "Retrieve update information for all Confluence spaces")
    @ApiResponse(responseCode = "200", description = "List of space update information")
    public CompletableFuture<ResponseEntity<@NonNull List<SpaceUpdateInfoDto>>> getAllSpacesUpdateInfo(
            @Parameter(description = "Filter only spaces modified since last scan")
            @RequestParam(required = false, defaultValue = "false") boolean modifiedOnly
    ) {
        log.info("GET request /spaces/update-info (modifiedOnly={})", modifiedOnly);

        return confluenceSpaceUpdateInfoPort.getAllSpacesUpdateInfo()
            .thenApply(updateInfos -> {
                var filteredInfos = modifiedOnly
                    ? updateInfos.stream().filter(SpaceUpdateInfo::hasBeenUpdated).toList()
                    : updateInfos;
                return ResponseEntity.ok(ConfluenceApiMapper.toDtoSpaceUpdateInfos(filteredInfos));
            })
            .exceptionally(ex -> {
                log.error("Error retrieving spaces update info", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GetMapping("/spaces/{spaceKey}/update-info")
    @Operation(summary = "Retrieve update information for a specific Confluence space")
    @ApiResponse(responseCode = "200", description = "Space update information")
    @ApiResponse(responseCode = "404", description = "Space not found")
    public CompletableFuture<ResponseEntity<@NonNull SpaceUpdateInfoDto>> getSpaceUpdateInfo(
            @Parameter(description = "Space key") @PathVariable String spaceKey) {

        log.info("GET request /spaces/{}/update-info", spaceKey);

        return confluenceSpaceUpdateInfoPort.getSpaceUpdateInfo(spaceKey)
            .thenApply(optionalUpdateInfo ->
                optionalUpdateInfo
                    .map(ConfluenceApiMapper::toDto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build())
            )
            .exceptionally(ex -> {
                log.error("Error retrieving update info for space {}", spaceKey, ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    public record ConfluenceHealthCheckResponse(String status, String message) { }
}