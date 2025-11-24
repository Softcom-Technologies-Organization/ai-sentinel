package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

class ConfluenceSpaceEntityMapperTest {

    @Test
    @DisplayName("Should_ReturnNull_When_EntityIsNull")
    void Should_ReturnNull_When_EntityIsNull() {
        // When
        ConfluenceSpace result = ConfluenceSpaceEntityMapper.toDomain(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_MapAllFieldsAndDefaults_When_EntityIsProvided")
    void Should_MapAllFieldsAndDefaults_When_EntityIsProvided() {
        // Given
        LocalDateTime lastModifiedDate = LocalDateTime.of(2024, 12, 25, 10, 15, 30);
        ConfluenceSpaceEntity entity = ConfluenceSpaceEntity.builder()
            .id("ID-1")
            .spaceKey("SPACE")
            .name("My Space")
            .url("https://confluence.local/space")
            .description("Space description")
            .cacheTimestamp(LocalDateTime.now().minusDays(1))
            .lastUpdated(LocalDateTime.now().minusHours(1))
            .lastModifiedDate(lastModifiedDate)
            .build();

        // When
        ConfluenceSpace domain = ConfluenceSpaceEntityMapper.toDomain(entity);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain).isNotNull();
        softly.assertThat(domain.id()).isEqualTo("ID-1");
        softly.assertThat(domain.key()).isEqualTo("SPACE");
        softly.assertThat(domain.name()).isEqualTo("My Space");
        softly.assertThat(domain.url()).isEqualTo("https://confluence.local/space");
        softly.assertThat(domain.description()).isEqualTo("Space description");
        // Defaults enforced by mapper
        softly.assertThat(domain.type()).isEqualTo(ConfluenceSpace.SpaceType.GLOBAL);
        softly.assertThat(domain.status()).isEqualTo(ConfluenceSpace.SpaceStatus.CURRENT);
        // Date conversion using system default zone
        Instant expectedInstant = lastModifiedDate.atZone(ZoneId.systemDefault()).toInstant();
        softly.assertThat(domain.lastModified()).isEqualTo(expectedInstant);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_SetNullLastModified_When_EntityHasNoLastModifiedDate")
    void Should_SetNullLastModified_When_EntityHasNoLastModifiedDate() {
        // Given
        ConfluenceSpaceEntity entity = ConfluenceSpaceEntity.builder()
            .id("ID-2")
            .spaceKey("SPACE2")
            .name("Another Space")
            .cacheTimestamp(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .lastModifiedDate(null)
            .build();

        // When
        ConfluenceSpace domain = ConfluenceSpaceEntityMapper.toDomain(entity);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain).isNotNull();
        softly.assertThat(domain.lastModified()).isNull();
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_ReturnNull_When_DomainIsNull")
    void Should_ReturnNull_When_DomainIsNull() {
        // When
        ConfluenceSpaceEntity result = ConfluenceSpaceEntityMapper.toEntity(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_MapAllFieldsAndDates_When_DomainIsProvided")
    void Should_MapAllFieldsAndDates_When_DomainIsProvided() {
        // Given
        Instant lastModified = Instant.parse("2025-01-01T12:00:00Z");
        ConfluenceSpace domain = new ConfluenceSpace(
            "ID-3",
            "KEY3",
            "Space 3",
            "https://confluence.local/space3",
            "Desc 3",
            ConfluenceSpace.SpaceType.PROJECT,
            ConfluenceSpace.SpaceStatus.ARCHIVED,
            new DataOwners.Loaded(List.of()),
            lastModified
        );

        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        // When
        ConfluenceSpaceEntity entity = ConfluenceSpaceEntityMapper.toEntity(domain);

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity).isNotNull();
        softly.assertThat(entity.getId()).isEqualTo("ID-3");
        softly.assertThat(entity.getSpaceKey()).isEqualTo("KEY3");
        softly.assertThat(entity.getName()).isEqualTo("Space 3");
        softly.assertThat(entity.getUrl()).isEqualTo("https://confluence.local/space3");
        softly.assertThat(entity.getDescription()).isEqualTo("Desc 3");
        // Timestamps set to now by mapper, so should be within [before, after]
        softly.assertThat(entity.getCacheTimestamp()).isBetween(before, after);
        softly.assertThat(entity.getLastUpdated()).isBetween(before, after);
        // lastModified conversion
        LocalDateTime expectedLdt = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
        softly.assertThat(entity.getLastModifiedDate()).isEqualTo(expectedLdt);
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_SetNullLastModifiedDate_When_DomainHasNoLastModified")
    void Should_SetNullLastModifiedDate_When_DomainHasNoLastModified() {
        // Given
        ConfluenceSpace domain = new ConfluenceSpace(
            "ID-4",
            "KEY4",
            "Space 4",
            null,
            null,
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            new DataOwners.Loaded(List.of()),
            null
        );

        // When
        ConfluenceSpaceEntity entity = ConfluenceSpaceEntityMapper.toEntity(domain);

        // Then
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity).isNotNull();
        softly.assertThat(entity.getLastModifiedDate()).isNull();
        softly.assertThat(entity.getCacheTimestamp()).isNotNull();
        softly.assertThat(entity.getLastUpdated()).isNotNull();
        softly.assertAll();
    }
}
