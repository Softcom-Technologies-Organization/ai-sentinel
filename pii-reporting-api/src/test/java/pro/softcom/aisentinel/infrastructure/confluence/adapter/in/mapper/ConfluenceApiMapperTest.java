package pro.softcom.aisentinel.infrastructure.confluence.adapter.in.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.confluence.SpaceUpdateInfo;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluencePageDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.ConfluenceSpaceDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.in.dto.SpaceUpdateInfoDto;

@DisplayName("Verifies Confluence API mapping behaviors")
class ConfluenceApiMapperTest {

    @Test
    @DisplayName("Maps a Confluence HTML page to a complete DTO")
    void Should_MapAllFields_When_PageHasHtmlContent() {
        // Given
        ConfluencePage page = ConfluencePage.builder()
            .id("123")
            .title("Test Page")
            .spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("<p>Test content</p>"))
            .labels(List.of("label1", "label2"))
            .build();

        // When
        ConfluencePageDto result = ConfluenceApiMapper.toDto(page);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("123");
        assertThat(result.title()).isEqualTo("Test Page");
        assertThat(result.spaceKey()).isEqualTo("TEST");
        assertThat(result.content()).isEqualTo("<p>Test content</p>");
        assertThat(result.contentFormat()).isEqualTo("storage");
        assertThat(result.labels()).containsExactly("label1", "label2");
    }

    @Test
    @DisplayName("Represents a page without content with null content fields")
    void Should_HandleNullContent_When_PageHasNoContent() {
        // Given
        ConfluencePage page = ConfluencePage.builder()
            .id("123")
            .title("Test Page")
            .spaceKey("TEST")
            .content(null)
            .labels(List.of())
            .build();

        // When
        ConfluencePageDto result = ConfluenceApiMapper.toDto(page);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.content()).isNull();
        assertThat(result.contentFormat()).isNull();
    }

    @Test
    @DisplayName("Returns an empty list when no pages are provided")
    void Should_ReturnEmptyList_When_PagesIsNull() {
        // When
        List<ConfluencePageDto> result = ConfluenceApiMapper.toDtoPages(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns an empty list when the pages list is empty")
    void Should_ReturnEmptyList_When_PagesIsEmpty() {
        // When
        List<ConfluencePageDto> result = ConfluenceApiMapper.toDtoPages(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Maps each page to DTO when a list of pages is provided")
    void Should_MapAllPages_When_PagesListIsProvided() {
        // Given
        List<ConfluencePage> pages = List.of(
            ConfluencePage.builder()
                .id("1")
                .title("Page 1")
                .spaceKey("TEST")
                .content(new ConfluencePage.HtmlContent("Content 1"))
                .labels(List.of())
                .build(),
            ConfluencePage.builder()
                .id("2")
                .title("Page 2")
                .spaceKey("TEST")
                .content(new ConfluencePage.WikiContent("Content 2"))
                .labels(List.of())
                .build()
        );

        // When
        List<ConfluencePageDto> result = ConfluenceApiMapper.toDtoPages(pages);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().id()).isEqualTo("1");
        assertThat(result.get(0).title()).isEqualTo("Page 1");
        assertThat(result.get(0).contentFormat()).isEqualTo("storage");
        assertThat(result.get(1).id()).isEqualTo("2");
        assertThat(result.get(1).title()).isEqualTo("Page 2");
        assertThat(result.get(1).contentFormat()).isEqualTo("wiki");
    }

    @Test
    @DisplayName("Maps a Confluence space to a complete DTO")
    void Should_MapAllFields_When_SpaceIsProvided() {
        // Given
        Instant now = Instant.now();
        ConfluenceSpace space = new ConfluenceSpace(
            "123",
            "TEST",
            "Test Space",
            "https://confluence.test/spaces/TEST",
            "Test description",
            ConfluenceSpace.SpaceType.GLOBAL,
            ConfluenceSpace.SpaceStatus.CURRENT,
            new DataOwners.Loaded(List.of()),
            now
        );

        // When
        ConfluenceSpaceDto result = ConfluenceApiMapper.toDto(space);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("123");
        assertThat(result.key()).isEqualTo("TEST");
        assertThat(result.name()).isEqualTo("Test Space");
        assertThat(result.url()).isEqualTo("https://confluence.test/spaces/TEST");
        assertThat(result.description()).isEqualTo("Test description");
        assertThat(result.type()).isEqualTo("global");
        assertThat(result.status()).isEqualTo("current");
    }

    @Test
    @DisplayName("Defaults space type and status when values are null")
    void Should_HandleNullTypeAndStatus_When_SpaceHasNullValues() {
        // Given - Le constructeur compact de ConfluenceSpace définit des valeurs par défaut
        // donc on teste avec les valeurs par défaut qui sont non-nulles
        ConfluenceSpace space = new ConfluenceSpace(
            "123",
            "TEST",
            "Test Space",
            null,
            null,
            null,
            null,
            null,
            null
        );

        // When
        ConfluenceSpaceDto result = ConfluenceApiMapper.toDto(space);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo("global"); // Valeur par défaut
        assertThat(result.status()).isEqualTo("current"); // Valeur par défaut
    }

    @Test
    @DisplayName("Returns an empty list when no spaces are provided")
    void Should_ReturnEmptyList_When_SpacesIsNull() {
        // When
        List<ConfluenceSpaceDto> result = ConfluenceApiMapper.toDtoSpaces(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns an empty list when the spaces list is empty")
    void Should_ReturnEmptyList_When_SpacesIsEmpty() {
        // When
        List<ConfluenceSpaceDto> result = ConfluenceApiMapper.toDtoSpaces(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Maps each space to DTO when a list of spaces is provided")
    void Should_MapAllSpaces_When_SpacesListIsProvided() {
        // Given
        List<ConfluenceSpace> spaces = List.of(
            new ConfluenceSpace(
                "1",
                "SPACE1",
                "Space 1",
                "url1",
                "desc1",
                ConfluenceSpace.SpaceType.GLOBAL,
                ConfluenceSpace.SpaceStatus.CURRENT,
                new DataOwners.Loaded(List.of()),
                Instant.now()
            ),
            new ConfluenceSpace(
                "2",
                "SPACE2",
                "Space 2",
                "url2",
                "desc2",
                ConfluenceSpace.SpaceType.PERSONAL,
                ConfluenceSpace.SpaceStatus.ARCHIVED,
                new DataOwners.Loaded(List.of()),
                Instant.now()
            )
        );

        // When
        List<ConfluenceSpaceDto> result = ConfluenceApiMapper.toDtoSpaces(spaces);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().key()).isEqualTo("SPACE1");
        assertThat(result.get(0).type()).isEqualTo("global");
        assertThat(result.get(0).status()).isEqualTo("current");
        assertThat(result.get(1).key()).isEqualTo("SPACE2");
        assertThat(result.get(1).type()).isEqualTo("personal");
        assertThat(result.get(1).status()).isEqualTo("archived");
    }

    @Test
    @DisplayName("Maps space update information to a complete DTO")
    void Should_MapAllFields_When_UpdateInfoIsProvided() {
        // Given
        Instant lastModified = Instant.parse("2024-01-15T10:00:00Z");
        Instant lastScanDate = Instant.parse("2024-01-10T10:00:00Z");
        SpaceUpdateInfo updateInfo = new SpaceUpdateInfo(
            "TEST",
            "Test Space",
            true,
            lastModified,
            lastScanDate,
            List.of("page1", "page2"),
            List.of("attachment1")
        );

        // When
        SpaceUpdateInfoDto result = ConfluenceApiMapper.toDto(updateInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaceKey()).isEqualTo("TEST");
        assertThat(result.spaceName()).isEqualTo("Test Space");
        assertThat(result.hasBeenUpdated()).isTrue();
        assertThat(result.lastModified()).isEqualTo(lastModified);
        assertThat(result.lastScanDate()).isEqualTo(lastScanDate);
        assertThat(result.updatedPages()).containsExactly("page1", "page2");
        assertThat(result.updatedAttachments()).containsExactly("attachment1");
    }

    @Test
    @DisplayName("Maps update info without optional details to a DTO with null optional fields")
    void Should_MapWithNullOptionalFields_When_UpdateInfoHasNoDetails() {
        // Given
        SpaceUpdateInfo updateInfo = new SpaceUpdateInfo(
            "TEST",
            "Test Space",
            false,
            null,
            null,
            null,
            null
        );

        // When
        SpaceUpdateInfoDto result = ConfluenceApiMapper.toDto(updateInfo);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.spaceKey()).isEqualTo("TEST");
        assertThat(result.hasBeenUpdated()).isFalse();
        assertThat(result.lastModified()).isNull();
        assertThat(result.lastScanDate()).isNull();
        assertThat(result.updatedPages()).isNull();
        assertThat(result.updatedAttachments()).isNull();
    }

    @Test
    @DisplayName("Returns an empty list when no update infos are provided")
    void Should_ReturnEmptyList_When_UpdateInfosIsNull() {
        // When
        List<SpaceUpdateInfoDto> result = ConfluenceApiMapper.toDtoSpaceUpdateInfos(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmptyList_When_UpdateInfosIsEmpty")
    void Should_ReturnEmptyList_When_UpdateInfosIsEmpty() {
        // When
        List<SpaceUpdateInfoDto> result = ConfluenceApiMapper.toDtoSpaceUpdateInfos(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_MapAllUpdateInfos_When_ListIsProvided")
    void Should_MapAllUpdateInfos_When_ListIsProvided() {
        // Given
        List<SpaceUpdateInfo> updateInfos = List.of(
            new SpaceUpdateInfo(
                "SPACE1",
                "Space 1",
                true,
                Instant.now(),
                Instant.now().minusSeconds(3600),
                List.of("page1"),
                List.of()
            ),
            new SpaceUpdateInfo(
                "SPACE2",
                "Space 2",
                false,
                null,
                Instant.now(),
                null,
                null
            )
        );

        // When
        List<SpaceUpdateInfoDto> result = ConfluenceApiMapper.toDtoSpaceUpdateInfos(updateInfos);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).spaceKey()).isEqualTo("SPACE1");
        assertThat(result.get(0).hasBeenUpdated()).isTrue();
        assertThat(result.get(1).spaceKey()).isEqualTo("SPACE2");
        assertThat(result.get(1).hasBeenUpdated()).isFalse();
    }
}
