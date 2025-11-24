package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper;

import java.util.List;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpaceDataOwner;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;

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
                .filter(ConfluenceDataOwnerMapper::hasSpaceAdministrationPermission)
                .filter(ConfluenceDataOwnerMapper::hasValidSubjects)
                .flatMap(p -> p.subjects().user().results().stream())
                .filter(ConfluenceDataOwnerMapper::isActiveAtlassianUserWithEmail)
                .map(u -> new ConfluenceSpaceDataOwner(u.accountId(), u.publicName(), u.email()))
                .distinct()
                .toList();
    }

    /**
     * Checks if permission grants space administration rights.
     * Business purpose: Identifies users who can manage the space and should be notified of PII findings.
     */
    private static boolean hasSpaceAdministrationPermission(ConfluenceSpaceDto.PermissionDto permission) {
        if (permission.operation() == null) {
            return false;
        }

        return OPERATION_SPACE.equalsIgnoreCase(permission.operation().targetType())
                && OPERATION_ADMINISTER.equalsIgnoreCase(permission.operation().operation());
    }

    /**
     * Checks if permission has valid user subjects.
     * Business purpose: Ensures the permission references actual users that can be contacted.
     */
    private static boolean hasValidSubjects(ConfluenceSpaceDto.PermissionDto permission) {
        return permission.subjects() != null
                && permission.subjects().user() != null
                && permission.subjects().user().results() != null;
    }

    /**
     * Checks if user is an active Atlassian account with a valid email.
     * Business purpose: Filters out inactive accounts and ensures we can contact the user.
     */
    private static boolean isActiveAtlassianUserWithEmail(ConfluenceSpaceDto.UserDto user) {
        return USER_ACCOUNT_TYPE.equalsIgnoreCase(user.accountType())
                && USER_ACCOUNT_ACTIVE_STATUS.equalsIgnoreCase(user.accountStatus())
                && user.email() != null
                && !user.email().isEmpty();
    }
}
