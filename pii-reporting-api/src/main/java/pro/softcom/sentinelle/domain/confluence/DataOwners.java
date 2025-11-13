package pro.softcom.sentinelle.domain.confluence;

import java.util.List;

/**
 * Represents the loading state of data owners for a Confluence space.
 * This sealed interface ensures type-safe handling of loaded vs not-loaded states.
 * 
 * <p>Use cases:
 * <ul>
 *   <li>{@link NotLoaded} - Space loaded from database without permissions</li>
 *   <li>{@link Loaded} - Space loaded from API with permissions, list may be empty</li>
 * </ul>
 */
public sealed interface DataOwners {
    
    /**
     * Data owners have not been loaded yet.
     * This state indicates the space was retrieved from database without permissions data.
     * An API call with expand=permissions is needed to load the actual data owners.
     */
    record NotLoaded() implements DataOwners {}
    
    /**
     * Data owners have been loaded from Confluence API.
     * The list may be empty if no data owners exist for this space (legitimate case).
     * 
     * @param owners list of data owners, empty if space has no administrators
     */
    record Loaded(List<ConfluenceSpaceDataOwner> owners) implements DataOwners {
        public Loaded {
            if (owners == null) {
                throw new IllegalArgumentException("Owners list cannot be null. Use empty list instead.");
            }
        }
    }
}
