package pro.softcom.aisentinel.domain.confluence;

import lombok.Getter;

import java.time.Instant;

public record ConfluenceSpace(
    String id,
    String key,
    String name,
    String url,
    String description,
    SpaceType type,
    SpaceStatus status,
    DataOwners dataOwners,
    Instant lastModified
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

    public ConfluenceSpace {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Space key cannot be empty");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Space name cannot be empty");
        }
        if (type == null) {
            type = SpaceType.GLOBAL;
        }
        if (status == null) {
            status = SpaceStatus.CURRENT;
        }
    }
}
