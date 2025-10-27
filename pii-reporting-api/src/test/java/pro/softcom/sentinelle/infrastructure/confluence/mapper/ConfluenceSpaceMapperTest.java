package pro.softcom.sentinelle.infrastructure.confluence.mapper;

import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper.ConfluenceSpaceMapper;

class ConfluenceSpaceMapperTest {

    @Test
    @DisplayName("ConfluenceSpaceMapper maps all fields on happy path")
    void toDomainModel() {
        var dto = createConfluenceSpaceDto();

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.id()).isEqualTo("123");
        softly.assertThat(domain.key()).isEqualTo("KEY");
        softly.assertThat(domain.name()).isEqualTo("My Space");
        softly.assertThat(domain.description()).isEqualTo("My space description");
        softly.assertThat(domain.type()).isEqualTo(ConfluenceSpace.SpaceType.PROJECT);
        softly.assertThat(domain.status()).isEqualTo(ConfluenceSpace.SpaceStatus.ARCHIVED);
        softly.assertAll();
    }

    private static ConfluenceSpaceDto createConfluenceSpaceDto() {
        var description = new ConfluenceSpaceDto.DescriptionDto(
            new ConfluenceSpaceDto.PlainTextDto("My space description", "plain"),
            new ConfluenceSpaceDto.ViewDto("<p>My space description</p>", "storage")
        );

        var permissions = List.of(
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("read", "space")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("update", "space")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("delete", "space")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("administer", "space")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("create", "page")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("comment", "page")),
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto("unknown", "space"))
        );

        return new ConfluenceSpaceDto(
            "123", "KEY", "My Space", "project", "archived",
            description,
            permissions,
            Map.of("k1", 1, "k2", "v2"),
            Map.of("self", "/rest/api/space/123"),
            null
        );
    }

    @Test
    @DisplayName("ConfluenceSpaceMapper applies defaults for null or unknown fields")
    void toDomainModel_defaultsAndUnknowns() {
        var description = new ConfluenceSpaceDto.DescriptionDto(null, null);
        var permissions = List.of(
            new ConfluenceSpaceDto.PermissionDto(null, new ConfluenceSpaceDto.OperationDto(null, null)),
            new ConfluenceSpaceDto.PermissionDto(null, null)
        );

        var dtoUnknownTypeStatus = new ConfluenceSpaceDto(
            "id2", "KEY2", "Space2", "not-known", "mystatus",
            description, permissions, null, null, null
        );

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dtoUnknownTypeStatus);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.type()).as("unknown type -> GLOBAL").isEqualTo(ConfluenceSpace.SpaceType.GLOBAL);
        softly.assertThat(domain.status()).as("unknown status -> CURRENT").isEqualTo(ConfluenceSpace.SpaceStatus.CURRENT);
        softly.assertThat(domain.description()).as("no plain description -> empty").isEmpty();
        softly.assertAll();
    }

    @Test
    @DisplayName("null type/status -> defaults and keeps other fields")
    void nullTypeStatus() {
        var dto = new ConfluenceSpaceDto(
                "id3", "KEY3", "Space3", null, null,
                new ConfluenceSpaceDto.DescriptionDto(new ConfluenceSpaceDto.PlainTextDto("desc", "plain"), null),
                List.of(),
                Map.of(),
                Map.of(),
                null
        );

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.type()).isEqualTo(ConfluenceSpace.SpaceType.GLOBAL);
        softly.assertThat(domain.status()).isEqualTo(ConfluenceSpace.SpaceStatus.CURRENT);
        softly.assertThat(domain.description()).isEqualTo("desc");
        softly.assertAll();
    }
}
