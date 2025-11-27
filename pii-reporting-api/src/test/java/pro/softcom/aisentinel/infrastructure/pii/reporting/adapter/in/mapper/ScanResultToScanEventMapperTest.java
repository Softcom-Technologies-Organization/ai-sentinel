package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ConfluenceContentScanResultEventDto;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

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
        List<DetectedPersonallyIdentifiableInformation> entities = List.of(entity());
        ConfluenceContentScanResult sr = ConfluenceContentScanResult.builder()
                .scanId("sid")
                .spaceKey("space")
                .eventType(ScanEventType.START.toJson())
                .isFinal(false)
                .pagesTotal(10)
                .pageIndex(3)
                .pageId("pid")
                .pageTitle("Title")
                .detectedPIIList(entities)
                .nbOfDetectedPIIBySeverity(summary)
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
        ConfluenceContentScanResultEventDto dto = mapper.toDto(sr);

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
        softly.assertThat(dto.detectedPIIList()).isEqualTo(entities);
        softly.assertThat(dto.nbOfDetectedPIIBySeverity()).isEqualTo(summary);
        softly.assertThat(dto.message()).isEqualTo("msg");
        softly.assertThat(dto.pageUrl()).isEqualTo("url");
        softly.assertThat(dto.emittedAt()).isEqualTo("emittedAt");
        softly.assertThat(dto.attachmentName()).isEqualTo("attName");
        softly.assertThat(dto.attachmentType()).isEqualTo("attType");
        softly.assertThat(dto.attachmentUrl()).isEqualTo("attUrl");
        softly.assertThat(dto.analysisProgressPercentage()).isEqualTo(42.0);
        softly.assertAll();
    }

    private static DetectedPersonallyIdentifiableInformation entity() {
        return new DetectedPersonallyIdentifiableInformation(
            0,
            1,
            ((Object) "EMAIL").toString(),
            ((Object) "EMAIL").toString(),
            0,
            null,  // detectedValue
            null,  // context
            null   // maskedContext
        );
    }
}
