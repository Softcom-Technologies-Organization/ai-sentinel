package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper;

import pro.softcom.sentinelle.domain.confluence.ConfluenceSpaceDataOwner;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;

import java.util.List;

/**
 * Maps Confluence space permissions to data owners.
 * Extracts users with space administration permissions who should be notified when PII is detected.
 */
public final class ConfluenceDataOwnerMapper {

    private static final String OPERATION_SPACE = "space";
    private static final String OPERATION_ADMINISTER = "administer";
    private static final String USER_ACCOUNT_TYPE = "atlassian";
    private static final String USER_ACCOUNT_ACTIVE_STATUS = "active";

    private ConfluenceDataOwnerMapper() {
        // utility class
    }

    /**
     * Extracts data owners from Confluence space permissions.
     * Data owners are identified as active Atlassian users with space administration permissions.
     *
     * @param dto the Confluence space DTO containing permissions
     * @return list of data owners, empty if no permissions or no matching users
     */
    public static List<ConfluenceSpaceDataOwner> extractDataOwners(ConfluenceSpaceDto dto) {
        if (dto == null || dto.permissions() == null) {
            return List.of();
        }

        return dto.permissions().stream()
                // Filter for space administration permissions
                .filter(p -> p.operation() != null
                        && OPERATION_SPACE.equalsIgnoreCase(p.operation().targetType())
                        && OPERATION_ADMINISTER.equalsIgnoreCase(p.operation().operation())
                )

                // Extract active Atlassian users
                .filter(p -> p.subjects() != null
                        && p.subjects().user() != null
                        && p.subjects().user().results() != null
                )
                .flatMap(p -> p.subjects().user().results().stream())
                .filter(u -> USER_ACCOUNT_TYPE.equalsIgnoreCase(u.accountType())
                        && USER_ACCOUNT_ACTIVE_STATUS.equalsIgnoreCase(u.accountStatus())
                        && !u.email().isEmpty()
                )

                // Map to domain
                .map(u -> new ConfluenceSpaceDataOwner(u.accountId(), u.publicName(), u.email()))
                .distinct()
                .toList();
    }
}
