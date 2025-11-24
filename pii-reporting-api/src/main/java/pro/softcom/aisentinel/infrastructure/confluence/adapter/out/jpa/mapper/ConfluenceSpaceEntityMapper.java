package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

public final class ConfluenceSpaceEntityMapper {

    private ConfluenceSpaceEntityMapper() {
    }

    public static ConfluenceSpace toDomain(ConfluenceSpaceEntity entity) {
        if (entity == null) {
            return null;
        }

        Instant lastModified = entity.getLastModifiedDate() != null
            ? entity.getLastModifiedDate().atZone(ZoneId.systemDefault()).toInstant()
            : null;

        return new ConfluenceSpace(
            entity.getId(),
            entity.getSpaceKey(),
            entity.getName(),
            entity.getUrl(),
            entity.getDescription(),
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            new DataOwners.NotLoaded(),
            lastModified
        );
    }

    public static ConfluenceSpaceEntity toEntity(ConfluenceSpace domain) {
        if (domain == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastModifiedDate = domain.lastModified() != null
            ? LocalDateTime.ofInstant(domain.lastModified(), ZoneId.systemDefault())
            : null;

        return ConfluenceSpaceEntity.builder()
            .id(domain.id())
            .spaceKey(domain.key())
            .name(domain.name())
            .url(domain.url())
            .description(domain.description())
            .cacheTimestamp(now)
            .lastUpdated(now)
            .lastModifiedDate(lastModifiedDate)
            .build();
    }
}
