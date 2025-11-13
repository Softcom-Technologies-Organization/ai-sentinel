package pro.softcom.sentinelle.domain.pii.export;

import lombok.Builder;

/**
 * Represents a contact person for a data source where PII has been detected.
 * These contacts should be notified when PII is found in their data source,
 * as they are responsible for taking appropriate action.
 */
@Builder
public record DataSourceContact(
        String displayName,
        String email
) {
    public DataSourceContact {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Contact display name cannot be empty");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Contact email cannot be empty");
        }
    }
}
