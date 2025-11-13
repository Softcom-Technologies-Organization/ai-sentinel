package pro.softcom.sentinelle.application.confluence.port.in;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import pro.softcom.sentinelle.domain.confluence.SpaceUpdateInfo;

/**
 * Use case for retrieving update information about Confluence spaces.
 * 
 * Business purpose: Allows the frontend to detect which spaces have been updated
 * since their last scan, enabling users to identify content that may need re-scanning.
 */
public interface GetSpaceUpdateInfoUseCase {
    
    /**
     * Gets update information for all spaces.
     * Compares the last modification date of each space with the date of its last scan
     * to determine if updates have occurred.
     * 
     * @return A list of SpaceUpdateInfo for all spaces
     */
    CompletableFuture<List<SpaceUpdateInfo>> getAllSpacesUpdateInfo();
    
    /**
     * Gets update information for a specific space.
     * 
     * @param spaceKey The unique key of the space
     * @return SpaceUpdateInfo for the specified space, or empty if space not found
     */
    CompletableFuture<java.util.Optional<SpaceUpdateInfo>> getSpaceUpdateInfo(String spaceKey);
}
