package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa.mapper;

import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.DataOwners;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

import java.time.LocalDateTime;

public final class ConfluenceSpaceEntityMapper {

    private ConfluenceSpaceEntityMapper() {
    }

    public static ConfluenceSpace toDomain(ConfluenceSpaceEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ConfluenceSpace(
            entity.getId(),
            entity.getSpaceKey(),
            entity.getName(),
            entity.getUrl(),
            entity.getDescription(),
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            new DataOwners.NotLoaded()
        );
    }

    public static ConfluenceSpaceEntity toEntity(ConfluenceSpace domain) {
        if (domain == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        return ConfluenceSpaceEntity.builder()
            .id(domain.id())
            .spaceKey(domain.key())
            .name(domain.name())
            .url(domain.url())
            .description(domain.description())
            .cacheTimestamp(now)
            .lastUpdated(now)
            .build();
    }
}
