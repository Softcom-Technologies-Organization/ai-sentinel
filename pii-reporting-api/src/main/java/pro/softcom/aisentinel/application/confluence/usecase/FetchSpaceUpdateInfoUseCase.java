package pro.softcom.aisentinel.application.confluence.usecase;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpacePort;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpaceUpdateInfoPort;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ModifiedPageInfo;
import pro.softcom.aisentinel.domain.confluence.SpaceUpdateInfo;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;

/**
 * Service responsible for determining if Confluence spaces have been updated since their last scan.
 * Business purpose: Enables the dashboard to show visual indicators for spaces that may need
 * re-scanning due to recent updates.
 */
@RequiredArgsConstructor
@Slf4j
public class FetchSpaceUpdateInfoUseCase implements ConfluenceSpaceUpdateInfoPort {

    private final ConfluenceSpacePort confluenceSpacePort;
    private final ConfluenceClient confluenceClient;
    private final ScanCheckpointRepository scanCheckpointRepository;

    @Override
    public CompletableFuture<List<SpaceUpdateInfo>> getAllSpacesUpdateInfo() {
        log.debug("Getting update info for all spaces");
        
        return confluenceSpacePort.getAllSpaces()
            .thenCompose(spaces -> {
                List<CompletableFuture<SpaceUpdateInfo>> futures = spaces.stream()
                    .map(this::buildSpaceUpdateInfo)
                    .toList();
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
            });
    }

    @Override
    public CompletableFuture<Optional<SpaceUpdateInfo>> getSpaceUpdateInfo(String spaceKey) {
        log.debug("Getting update info for space: {}", spaceKey);
        
        return confluenceSpacePort.getSpace(spaceKey)
            .thenCompose(optionalSpace -> optionalSpace.map(confluenceSpace -> buildSpaceUpdateInfo(confluenceSpace)
                    .thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    /**
     * Builds SpaceUpdateInfo by comparing space's last modification with its last scan date.
     * Uses CQL Content Search API to find the most recent page modification in the space.
     * Business logic:
     * - If no scan exists: hasBeenUpdated = false (nothing to compare against)
     * - If no modifications found via CQL: hasBeenUpdated = false (unable to determine)
     * - If lastModified > lastScanDate: hasBeenUpdated = true (space has new content)
     * - Otherwise: hasBeenUpdated = false (space unchanged)
     */
    private CompletableFuture<SpaceUpdateInfo> buildSpaceUpdateInfo(ConfluenceSpace space) {
        String spaceKey = space.key();
        String spaceName = space.name();

        Optional<Instant> lastScanDate = findLastScanDate(spaceKey);

        if (lastScanDate.isEmpty()) {
            log.debug("No completed scan found for space {}", spaceKey);
            return CompletableFuture.completedFuture(
                SpaceUpdateInfo.noScanYet(spaceKey, spaceName, null)
            );
        }

        Instant since = lastScanDate.get();

        return confluenceClient.getModifiedPagesSince(spaceKey, since)
            .thenCompose(modifiedPages -> {
                if (modifiedPages == null || modifiedPages.isEmpty()) {
                    log.debug("No modifications found for space {} since last scan at {}", spaceKey, since);
                    return CompletableFuture.completedFuture(
                        SpaceUpdateInfo.noUpdates(spaceKey, spaceName, null, since)
                    );
                }

                List<String> updatedPageTitles = modifiedPages.stream()
                    .map(ModifiedPageInfo::title)
                    .toList();

                Instant mostRecentModification = modifiedPages.stream()
                    .map(ModifiedPageInfo::lastModified)
                    .filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(since);

                return confluenceClient.getModifiedAttachmentsSince(spaceKey, since)
                    .exceptionally(ex -> {
                        log.warn("Failed to retrieve modified attachments for space {}: {}", spaceKey, ex.getMessage());
                        return List.of();
                    })
                    .thenApply(modifiedAttachments -> {
                        List<String> updatedAttachmentNames = (modifiedAttachments == null)
                            ? List.of()
                            : modifiedAttachments.stream()
                                .map(ModifiedAttachmentInfo::title)
                                .toList();

                        return SpaceUpdateInfo.withUpdates(
                            spaceKey,
                            spaceName,
                            mostRecentModification,
                            since,
                            updatedPageTitles,
                            updatedAttachmentNames
                        );
                    });
            });
    }


    /**
     * Finds the date of the most recent completed scan for a space.
     *
     * @param spaceKey The space key to search for
     * @return The date of the last completed scan, or empty if no completed scan exists
     */
    private Optional<Instant> findLastScanDate(String spaceKey) {
        try {
            return scanCheckpointRepository.findLatestBySpace(spaceKey)
                .map(ScanCheckpoint::updatedAt)
                .map(localDateTime -> localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            log.warn("Error retrieving scan checkpoint for space {}: {}", spaceKey, e.getMessage());
            return Optional.empty();
        }
    }
}
