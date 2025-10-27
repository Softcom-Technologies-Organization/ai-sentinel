package pro.softcom.sentinelle.domain.confluence;

import java.time.Instant;
import lombok.Getter;

public record ConfluenceSpace(
    String id,
    String key,
    String name,
    String url,
    String description,
    SpaceType type,
    SpaceStatus status,
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
    }
}
