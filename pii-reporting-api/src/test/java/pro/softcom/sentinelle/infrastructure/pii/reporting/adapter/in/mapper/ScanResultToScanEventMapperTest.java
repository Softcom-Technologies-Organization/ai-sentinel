package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        List<PiiEntity> entities = List.of(entity(0, 1, "EMAIL"));
        ScanResult sr = ScanResult.builder()
                .scanId("sid")
                .spaceKey("space")
                .eventType(ScanEventType.START.toJson())
                .isFinal(false)
                .pagesTotal(10)
                .pageIndex(3)
                .pageId("pid")
                .pageTitle("Title")
                .detectedEntities(entities)
                .summary(summary)
                .sourceContent("abc")
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
        softly.assertThat(dto.eventType()).isEqualTo("start");
        softly.assertThat(dto.isFinal()).isFalse();
        softly.assertThat(dto.pagesTotal()).isEqualTo(10);
        softly.assertThat(dto.pageIndex()).isEqualTo(3);
        softly.assertThat(dto.pageId()).isEqualTo("pid");
        softly.assertThat(dto.pageTitle()).isEqualTo("Title");
        softly.assertThat(dto.detectedEntities()).isEqualTo(entities);
        softly.assertThat(dto.summary()).isEqualTo(summary);
        softly.assertThat(dto.message()).isEqualTo("msg");
        softly.assertThat(dto.pageUrl()).isEqualTo("url");
        softly.assertThat(dto.emittedAt()).isEqualTo("emittedAt");
        softly.assertThat(dto.attachmentName()).isEqualTo("attName");
        softly.assertThat(dto.attachmentType()).isEqualTo("attType");
        softly.assertThat(dto.attachmentUrl()).isEqualTo("attUrl");
        softly.assertThat(dto.analysisProgressPercentage()).isEqualTo(42.0);
        softly.assertAll();
    }

    private static PiiEntity entity(int start, int end, Object type) {
        return new PiiEntity(
                start, 
                end, 
                type == null ? null : type.toString(), 
                type == null ? null : type.toString(), 
                0, 
                null,  // detectedValue
                null,  // context
                null   // maskedContext
        );
    }
}
