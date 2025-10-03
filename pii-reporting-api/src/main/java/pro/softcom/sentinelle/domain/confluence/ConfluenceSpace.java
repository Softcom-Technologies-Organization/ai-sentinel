package pro.softcom.sentinelle.domain.confluence;

import java.util.Map;
import lombok.Getter;

public record ConfluenceSpace(
    String id,
    String key,
    String name,
    String url,
    String description,
    SpaceType type,
    SpaceStatus status,
    SpacePermissions permissions,
    Map<String, Object> metadata
) {

    @Getter
    public enum SpaceType {
        GLOBAL("global"),
        PERSONAL("personal"),
        PROJECT("project"),
        TEAM("team");

        private final String value;

        SpaceType(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum SpaceStatus {
        CURRENT("current"),
        ARCHIVED("archived");

        private final String value;

        SpaceStatus(String value) {
            this.value = value;
        }
    }

    public record SpacePermissions(
        boolean canView,
        boolean canEdit,
        boolean canDelete,
        boolean canAdmin,
        boolean canCreatePage,
        boolean canComment
    ) {
        public boolean hasReadAccess() {
            return canView;
        }

        public boolean hasWriteAccess() {
            return canEdit || canCreatePage || canComment;
        }

        public boolean hasAdminAccess() {
            return canAdmin;
        }
    }

    public ConfluenceSpace {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La clé de l'espace ne peut pas être vide");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'espace ne peut pas être vide");
        }
        if (type == null) {
            type = SpaceType.GLOBAL;
        }
        if (status == null) {
            status = SpaceStatus.CURRENT;
        }
        if (permissions == null) {
            permissions = new SpacePermissions(false, false, false, false, false, false);
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
