package pro.softcom.aisentinel.infrastructure.confluence.mapper;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluencePageDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluencePageMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConfluencePageMapperTest {

    @Test
    void toDomainModel_ShouldMapAllFieldsWhenFullDto() {
        // arrange
        var user = new ConfluencePageDto.UserDto("known", "john.doe", "userKey", "John Doe");
        var version = new ConfluencePageDto.VersionDto(user, LocalDateTime.of(2025, 1, 2, 3, 4).toString(), 5, false, "msg");
        var storage = new ConfluencePageDto.ContentDto("<p>Hello</p>", "storage");
        var body = new ConfluencePageDto.BodyDto(storage, null);
        var space = new ConfluencePageDto.SpaceDto("SPACE", "My Space");
        Map<String, Object> metadata = Map.of("k1", "v1");
        var dto = new ConfluencePageDto(
                "123", "page", "current", "Title",
                space, body, version, metadata, Map.of("self", "/rest"));

        // act
        var domain = ConfluencePageMapper.toDomain(dto);
        
        // assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.id()).isEqualTo("123");
        softly.assertThat(domain.title()).isEqualTo("Title");
        softly.assertThat(domain.spaceKey()).isEqualTo("SPACE");
        softly.assertThat(domain.content()).isInstanceOf(ConfluencePage.HtmlContent.class);
        softly.assertThat(domain.content().body()).isEqualTo("<p>Hello</p>");
        softly.assertThat(domain.metadata()).isNotNull();
        softly.assertThat(domain.metadata().createdBy()).isEqualTo("john.doe");
        softly.assertThat(domain.metadata().lastModifiedBy()).isEqualTo("john.doe");
        softly.assertThat(domain.metadata().version()).isEqualTo(5);
        softly.assertThat(domain.metadata().status()).isEqualTo("current");
        softly.assertThat(domain.labels()).isEqualTo(List.of());
        softly.assertThat(domain.customProperties()).containsEntry("k1", "v1");
        softly.assertAll();
    }

    @Test
    void toDomainModel_ShouldHandleNullBodyAndVersion() {
        // arrange: body and version are null; space null too
        var dto = new ConfluencePageDto(
                null, "page", null, null,
                null, null, null, null, null);

        // act
        var domain = ConfluencePageMapper.toDomain(dto);

        // assert
        assertThat(domain.content()).isInstanceOf(ConfluencePage.HtmlContent.class);
        assertThat(domain.content().body()).isEmpty();
        assertThat(domain.metadata()).isNull();
        assertThat(domain.spaceKey()).isEmpty();
        assertThat(domain.customProperties()).isEmpty();
        assertThat(domain.labels()).isEmpty();
    }

    @Test
    void fromDomainModel_ShouldMapAllFieldsWhenMetadataPresent() {
        // arrange
        var meta = new ConfluencePage.PageMetadata("creator", LocalDateTime.of(2024, 12, 31, 23, 59),
                "modifier", LocalDateTime.of(2025, 1, 1, 0, 1), 7, "current");
        var page = new ConfluencePage(
                "id-1", "T1", "KEY",
                new ConfluencePage.HtmlContent("<p>X</p>"),
                meta,
                List.of("l1", "l2"),
                Map.of("a", 1)
        );

        // act
        var dto = ConfluencePageMapper.fromDomain(page);
        
        // assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(dto.id()).isEqualTo("id-1");
        softly.assertThat(dto.title()).isEqualTo("T1");
        softly.assertThat(dto.space().key()).isEqualTo("KEY");
        softly.assertThat(dto.body().storage().value()).isEqualTo("<p>X</p>");
        softly.assertThat(dto.body().storage().representation()).isEqualTo("storage");
        softly.assertThat(dto.version()).isNotNull();
        softly.assertThat(dto.version().by()).isNotNull();
        softly.assertThat(dto.version().by().username()).isEqualTo("modifier");
        softly.assertThat(dto.version().number()).isEqualTo(7);
        softly.assertThat(dto.status()).isEqualTo("current");
        softly.assertThat(dto.metadata()).containsEntry("a", 1);
        softly.assertAll();
    }

    @Test
    void fromDomainModel_ShouldFillDefaultsWhenMetadataNull() {
        // arrange
        var page = new ConfluencePage(
                null, "T2", "K",
                new ConfluencePage.MarkdownContent("body"),
                null,
                List.of(),
                Map.of()
        );

        // act
        var dto = ConfluencePageMapper.fromDomain(page);

        // assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(dto.type()).isEqualTo("page");
        softly.assertThat(dto.status()).isEqualTo("current");
        softly.assertThat(dto.version()).isNotNull();
        softly.assertThat(dto.version().number()).isEqualTo(1);
        softly.assertThat(dto.body().storage().representation()).isEqualTo("markdown");
        softly.assertThat(dto.space().key()).isEqualTo("K");
        softly.assertAll();
    }
}
