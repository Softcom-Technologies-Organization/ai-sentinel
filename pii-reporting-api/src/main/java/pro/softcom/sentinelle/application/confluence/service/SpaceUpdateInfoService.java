package pro.softcom.sentinelle.application.confluence.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.in.GetSpaceUpdateInfoUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ModifiedPageInfo;
import pro.softcom.sentinelle.domain.confluence.SpaceUpdateInfo;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;

/**
 * Service responsible for determining if Confluence spaces have been updated since their last scan.
 * 
 * Business purpose: Enables the dashboard to show visual indicators for spaces that may need
 * re-scanning due to recent updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceUpdateInfoService implements GetSpaceUpdateInfoUseCase {

    private final ConfluenceUseCase confluenceUseCase;
    private final ConfluenceClient confluenceClient;
    private final ScanCheckpointRepository scanCheckpointRepository;

    @Override
    public CompletableFuture<List<SpaceUpdateInfo>> getAllSpacesUpdateInfo() {
        log.debug("Getting update info for all spaces");
        
        return confluenceUseCase.getAllSpaces()
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
        
        return confluenceUseCase.getSpace(spaceKey)
            .thenCompose(optionalSpace -> optionalSpace.map(confluenceSpace -> buildSpaceUpdateInfo(confluenceSpace)
                    .thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    /**
     * Builds SpaceUpdateInfo by comparing space's last modification with its last scan date.
     * Uses CQL Content Search API to find the most recent page modification in the space.
     * 
     * Business logic:
     * - If no scan exists: hasBeenUpdated = false (nothing to compare against)
     * - If no modifications found via CQL: hasBeenUpdated = false (unable to determine)
     * - If lastModified > lastScanDate: hasBeenUpdated = true (space has new content)
     * - Otherwise: hasBeenUpdated = false (space unchanged)
     */
    private CompletableFuture<SpaceUpdateInfo> buildSpaceUpdateInfo(ConfluenceSpace space) {
        String spaceKey = space.key();
        String spaceName = space.name();

        Optional<Instant> lastScanDate = findLastCompletedScanDate(spaceKey);

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
     * Business rule: Only COMPLETED scans are considered, as interrupted scans
     * may not represent a full analysis of the space.
     * 
     * @param spaceKey The space key to search for
     * @return The date of the last completed scan, or empty if no completed scan exists
     */
    private Optional<Instant> findLastCompletedScanDate(String spaceKey) {
        try {
            // Note: This retrieves ALL scan checkpoints for the space across all scans
            // In a real implementation, we might want to add a query to get only the latest
            // completed checkpoint per space, for better performance
            List<ScanCheckpoint> checkpoints = scanCheckpointRepository.findBySpace(spaceKey);

            //FIXME need to decide what scan status needs to be filtered out
            return checkpoints.stream()
                .filter(Objects::nonNull)
                .filter(checkpoint -> checkpoint.spaceKey() != null && checkpoint.spaceKey().equals(spaceKey))
                .map(ScanCheckpoint::updatedAt)
                .filter(Objects::nonNull)
                .map(localDateTime -> localDateTime.atZone(ZoneId.systemDefault()).toInstant())
                .max(Instant::compareTo);
                
        } catch (Exception e) {
            log.warn("Error retrieving scan checkpoint for space {}: {}", spaceKey, e.getMessage());
            return Optional.empty();
        }
    }
}
