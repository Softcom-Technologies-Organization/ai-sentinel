package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

@ExtendWith(MockitoExtension.class)
class ScanResultToScanEventMapperTest {

    private final ScanResultToScanEventMapper mapper = new ScanResultToScanEventMapper();

    @Test
    void Should_ReturnNull_When_InputIsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void Should_MapAllFields_When_ScanResultProvided() {
        // Arrange
        Map<String, Integer> summary = Map.of("EMAIL", 2, "PHONE", 1);
        List<Map<String, Object>> entities = List.of(entity(0, 1, "EMAIL"));
        ScanResult sr = ScanResult.builder()
                .scanId("sid")
                .spaceKey("space")
                .eventType(ScanEventType.START.toJson())
                .isFinal(false)
                .pagesTotal(10)
                .pageIndex(3)
                .pageId("pid")
                .pageTitle("Title")
                .entities(entities)
                .summary(summary)
                .sourceContent("abc")
                .maskedContent("[EMAIL]bc")
                .message("msg")
                .pageUrl("url")
                .emittedAt("emittedAt")
                .attachmentName("attName")
                .attachmentType("attType")
                .attachmentUrl("attUrl")
                .analysisProgressPercentage(42.0)
                .build();

        // Act
        ScanEventDto dto = mapper.toDto(sr);

        // Assert (soft)
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(dto.scanId()).isEqualTo("sid");
        softly.assertThat(dto.spaceKey()).isEqualTo("space");
        softly.assertThat(dto.eventType()).isEqualTo(ScanEventType.START);
        softly.assertThat(dto.isFinal()).isFalse();
        softly.assertThat(dto.pagesTotal()).isEqualTo(10);
        softly.assertThat(dto.pageIndex()).isEqualTo(3);
        softly.assertThat(dto.pageId()).isEqualTo("pid");
        softly.assertThat(dto.pageTitle()).isEqualTo("Title");
        softly.assertThat(dto.entities()).isEqualTo(entities);
        softly.assertThat(dto.summary()).isEqualTo(summary);
        softly.assertThat(dto.maskedContent()).isEqualTo("[EMAIL]bc");
        softly.assertThat(dto.message()).isEqualTo("msg");
        softly.assertThat(dto.pageUrl()).isEqualTo("url");
        softly.assertThat(dto.emittedAt()).isEqualTo("emittedAt");
        softly.assertThat(dto.attachmentName()).isEqualTo("attName");
        softly.assertThat(dto.attachmentType()).isEqualTo("attType");
        softly.assertThat(dto.attachmentUrl()).isEqualTo("attUrl");
        softly.assertThat(dto.analysisProgressPercentage()).isEqualTo(42.0);
        softly.assertAll();
    }

    @Test
    void Should_UseProvidedMaskedContent_When_MaskedContentNotNull() {
        // Arrange
        List<Map<String, Object>> entities = List.of(entity(1, 3, "EMAIL"));
        ScanResult sr = ScanResult.builder()
                .sourceContent("abcde")
                .entities(entities)
                .maskedContent("GIVEN")
                .build();

        // Act
        ScanEventDto dto = mapper.toDto(sr);

        // Assert
        assertThat(dto.maskedContent()).isEqualTo("GIVEN");
    }

    @Test
    void Should_BuildMaskedContent_When_SourceAndEntitiesProvided() {
        // Arrange
        List<Map<String, Object>> entities = new ArrayList<>();
        // Intentionally unsorted to verify sorting by start
        entities.add(entity(3, 4, null)); // will become UNKNOWN
        entities.add(entity(1, 3, "EMAIL"));
        ScanResult sr = ScanResult.builder()
                .sourceContent("abcde")
                .entities(entities)
                .build();

        // Act
        ScanEventDto dto = mapper.toDto(sr);

        // Assert
        assertThat(dto.maskedContent()).isEqualTo("a[EMAIL][UNKNOWN]e");
    }

    @Test
    void Should_ClampAndInsertTokens_When_EntityBoundsAreOutsideSource() {
        // Arrange
        List<Map<String, Object>> entities = List.of(
                entity(-5, 2, "SSN"),
                entity(10, 12, "PHONE")
        );
        ScanResult sr = ScanResult.builder()
                .sourceContent("abcde")
                .entities(entities)
                .build();

        // Act
        ScanEventDto dto = mapper.toDto(sr);

        // Assert
        assertThat(dto.maskedContent()).isEqualTo("[SSN]cde[PHONE]");
    }

    @Test
    void Should_SetMaskedContentNull_When_SourceBlankOrEntitiesEmpty() {
        // blank source
        ScanResult sr1 = ScanResult.builder()
                .sourceContent("   ")
                .entities(List.of(entity(0, 1, "EMAIL")))
                .build();
        // empty entities
        ScanResult sr2 = ScanResult.builder()
                .sourceContent("abc")
                .entities(List.of())
                .build();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(mapper.toDto(sr1).maskedContent()).isNull();
        softly.assertThat(mapper.toDto(sr2).maskedContent()).isNull();
        softly.assertAll();
    }

    @Test
    void Should_TruncateMaskedContent_When_ResultExceedsLimit() {
        // Arrange: create long source (6000 chars)
        String source = "x".repeat(6000);
        List<Map<String, Object>> entities = List.of(entity(0, 1, "EMAIL"));
        ScanResult sr = ScanResult.builder()
                .sourceContent(source)
                .entities(entities)
                .build();

        // Act
        String masked = mapper.toDto(sr).maskedContent();

        // Assert
        assertThat(masked)
            .isNotNull()
            .hasSize(5001)
            .endsWith("…");
    }

    private static Map<String, Object> entity(int start, int end, Object type) {
        Map<String, Object> e = new HashMap<>();
        e.put("start", start);
        e.put("end", end);
        e.put("type", type);
        return e;
    }
}
