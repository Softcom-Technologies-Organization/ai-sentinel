package pro.softcom.aisentinel.infrastructure.confluence.mapper;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.mapper.ConfluenceSpaceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("Should_ThrowIllegalArgumentException_When_DtoIsNull")
//    @SuppressWarnings({"ConstantConditions", "DataFlowIssue"})
    void Should_ThrowIllegalArgumentException_When_DtoIsNull() {
        assertThatThrownBy(() -> ConfluenceSpaceMapper.toDomain(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should_MapPersonalSpace_When_TypeIsPersonal")
    void Should_MapPersonalSpace_When_TypeIsPersonal() {
        var dto = baseDtoBuilder("idP", "PERS", "Personal")
            .withType("PERSONAL")
            .withStatus("current")
            .withHistory(null)
            .build();

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.type()).isEqualTo(ConfluenceSpace.SpaceType.PERSONAL);
        softly.assertThat(domain.status()).isEqualTo(ConfluenceSpace.SpaceStatus.CURRENT);
        softly.assertThat(domain.lastModified()).isNull();
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_MapTeamSpace_When_TypeIsTeam")
    void Should_MapTeamSpace_When_TypeIsTeam() {
        var dto = baseDtoBuilder("idT", "TEAMK", "Team Space")
            .withType("team")
            .withStatus("CURRENT")
            .withHistory(null)
            .build();

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.type()).isEqualTo(ConfluenceSpace.SpaceType.TEAM);
        softly.assertThat(domain.status()).isEqualTo(ConfluenceSpace.SpaceStatus.CURRENT);
        softly.assertThat(domain.lastModified()).isNull();
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_ReturnLastModifiedFromLastUpdated_When_ParsableLastUpdated")
    void Should_ReturnLastModifiedFromLastUpdated_When_ParsableLastUpdated() {
        String when = "2024-01-02T03:04:05Z";
        var history = new ConfluenceSpaceDto.HistoryDto(
            null,
            new ConfluenceSpaceDto.LastUpdatedDto(when, null)
        );
        var dto = baseDtoBuilder("idL", "KEYL", "LastUpdated")
            .withType("project")
            .withStatus("current")
            .withHistory(history)
            .build();

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.lastModified()).isEqualTo(Instant.parse(when));
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_FallbackToCreatedDate_When_LastUpdatedUnparsableButCreatedDateParsable")
    void Should_FallbackToCreatedDate_When_LastUpdatedUnparsableButCreatedDateParsable() {
        String created = "2023-12-31T23:59:59Z";
        var history = new ConfluenceSpaceDto.HistoryDto(
            created,
            new ConfluenceSpaceDto.LastUpdatedDto("not-a-date", null)
        );
        var dto = baseDtoBuilder("idC", "KEYC", "CreatedDate")
            .withType("project")
            .withStatus("current")
            .withHistory(history)
            .build();

        ConfluenceSpace domain = ConfluenceSpaceMapper.toDomain(dto);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(domain.lastModified()).isEqualTo(Instant.parse(created));
        softly.assertAll();
    }

    @Test
    @DisplayName("Should_ReturnNullLastModified_When_HistoryMissingOrDatesInvalid")
    void Should_ReturnNullLastModified_When_HistoryMissingOrDatesInvalid() {
        // history with both fields null/invalid
        var historyInvalid = new ConfluenceSpaceDto.HistoryDto(
            null,
            new ConfluenceSpaceDto.LastUpdatedDto(null, null)
        );
        var dto1 = baseDtoBuilder("idN1", "K1", "NoHistory")
            .withType("project").withStatus("current").withHistory(null).build();
        var dto2 = baseDtoBuilder("idN2", "K2", "InvalidDates")
            .withType("project").withStatus("current").withHistory(historyInvalid).build();
        // createdDate is present but unparsable -> still null lastModified, and covers createdDate warn path
        var historyBadCreated = new ConfluenceSpaceDto.HistoryDto(
            "bad-created-date",
            null
        );
        var dto3 = baseDtoBuilder("idN3", "K3", "BadCreatedDate")
            .withType("project").withStatus("current").withHistory(historyBadCreated).build();

        ConfluenceSpace d1 = ConfluenceSpaceMapper.toDomain(dto1);
        ConfluenceSpace d2 = ConfluenceSpaceMapper.toDomain(dto2);
        ConfluenceSpace d3 = ConfluenceSpaceMapper.toDomain(dto3);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(d1.lastModified()).isNull();
        softly.assertThat(d2.lastModified()).isNull();
        softly.assertThat(d3.lastModified()).isNull();
        softly.assertAll();
    }

    // --- Test helpers ------------------------------------------------------
    private static DtoBuilder baseDtoBuilder(String id, String key, String name) {
        var description = new ConfluenceSpaceDto.DescriptionDto(
            new ConfluenceSpaceDto.PlainTextDto("desc", "plain"),
            new ConfluenceSpaceDto.ViewDto("<p>desc</p>", "storage")
        );
        return new DtoBuilder(id, key, name, description);
    }

    private static final class DtoBuilder {
        private String id;
        private String key;
        private String name;
        private String type;
        private String status;
        private ConfluenceSpaceDto.DescriptionDto description;
        private ConfluenceSpaceDto.HistoryDto history;

        private DtoBuilder(String id, String key, String name, ConfluenceSpaceDto.DescriptionDto description) {
            this.id = id;
            this.key = key;
            this.name = name;
            this.description = description;
            this.type = "project";
            this.status = "current";
        }

        DtoBuilder withType(String type) { this.type = type; return this; }
        DtoBuilder withStatus(String status) { this.status = status; return this; }
        DtoBuilder withHistory(ConfluenceSpaceDto.HistoryDto history) { this.history = history; return this; }

        ConfluenceSpaceDto build() {
            return new ConfluenceSpaceDto(
                id,
                key,
                name,
                type,
                status,
                description,
                List.of(),
                Map.of(),
                Map.of(),
                history
            );
        }
    }
}
