package pro.softcom.aisentinel.domain.confluence;

/**
 * Represents a data owner for a Confluence space.
 * Data owners are responsible for the data in the space and should be notified
 * when PII is detected in their data source.
 */
public record ConfluenceSpaceDataOwner(
        String accountId,
        String displayName,
        String email
) {
}
