package pro.softcom.aisentinel.application.pii.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.domain.pii.reporting.PiiEntity;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

@DisplayName("Detection report mapper tests")
class DetectionReportMapperTest {

    private DetectionReportMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DetectionReportMapper();
    }

    @ParameterizedTest
    @MethodSource("provideEmptyScenarios")
    @DisplayName("Should_ReturnEmptyList_When_NoEntitiesToMap")
    void Should_ReturnEmptyList_When_NoEntitiesToMap(ScanResult scanResult) {
        // Given (provided by parameter)

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(scanResult);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_MapSingleEntity_When_OneEntityDetected")
    void Should_MapSingleEntity_When_OneEntityDetected() {
        // Given
        PiiEntity entity = createPiiEntity("EMAIL", "Email", "john@example.com", 0.95);
        ScanResult scanResult = createScanResult(List.of(entity));

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(scanResult);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo("EMAIL");
        assertThat(result.getFirst().scanId()).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("provideMultipleEntities")
    @DisplayName("Should_MapAllEntities_When_MultipleEntitiesDetected")
    void Should_MapAllEntities_When_MultipleEntitiesDetected(List<PiiEntity> entities, int expectedCount) {
        // Given
        ScanResult scanResult = createScanResult(entities);

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(scanResult);

        // Then
        assertThat(result).hasSize(expectedCount);
    }

    @Test
    @DisplayName("Should_MapEntityFields_When_Mapping")
    void Should_MapEntityFields_When_Mapping() {
        // Given
        PiiEntity entity = createPiiEntity("EMAIL", "Email Label", "masked@example.com", 0.92);
        ScanResult scanResult = createScanResult(List.of(entity));

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(scanResult);

        // Then
        assertThat(result).hasSize(1);
        DetectionReportEntry entry = result.getFirst();
        assertThat(entry.type()).isEqualTo("EMAIL");
        assertThat(entry.typeLabel()).isNotBlank();
        assertThat(entry.maskedContext()).isNotBlank();
        assertThat(entry.confidenceScore()).isPositive();
    }

    @Test
    @DisplayName("Should_MapScanResultMetadata_When_Mapping")
    void Should_MapScanResultMetadata_When_Mapping() {
        // Given
        ScanResult scanResult = ScanResult.builder()
                .scanId("custom-scan-id")
                .spaceKey("CUSTOM-KEY")
                .emittedAt("2024-12-31T23:59:59Z")
                .pageTitle("Custom Page")
                .pageUrl("https://custom.com/page")
                .attachmentName("custom.doc")
                .attachmentUrl("https://custom.com/att")
                .detectedEntities(List.of(createPiiEntity("EMAIL", "Email", "test@test.com", 0.9)))
                .build();

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(scanResult);

        // Then
        assertThat(result).hasSize(1);
        DetectionReportEntry entry = result.getFirst();
        assertThat(entry.scanId()).isNotBlank();
        assertThat(entry.spaceKey()).isNotBlank();
        assertThat(entry.emittedAt()).isNotBlank();
    }

    private static Stream<Arguments> provideEmptyScenarios() {
        return Stream.of(
                Arguments.of((ScanResult) null),
                Arguments.of(ScanResult.builder().scanId("scan-123").spaceKey("TEST").detectedEntities(null).build()),
                Arguments.of(ScanResult.builder().scanId("scan-123").spaceKey("TEST").detectedEntities(List.of()).build())
        );
    }

    private static Stream<Arguments> provideMultipleEntities() {
        List<PiiEntity> scenario1 = List.of(
                createPiiEntity("EMAIL", "Email", "test@example.com", 0.9)
        );
        List<PiiEntity> scenario2 = List.of(
                createPiiEntity("EMAIL", "Email", "test@example.com", 0.9),
                createPiiEntity("PHONE", "Phone", "+33123456789", 0.85)
        );
        List<PiiEntity> scenario3 = List.of(
                createPiiEntity("EMAIL", "Email", "a@example.com", 0.9),
                createPiiEntity("PHONE", "Phone", "+33123456789", 0.85),
                createPiiEntity("NAME", "Name", "John Doe", 0.88)
        );

        return Stream.of(
                Arguments.of(scenario1, scenario1.size()),
                Arguments.of(scenario2, scenario2.size()),
                Arguments.of(scenario3, scenario3.size())
        );
    }

    private static PiiEntity createPiiEntity(String type, String label, String context, double confidence) {
        return PiiEntity.builder()
                .piiType(type)
                .piiTypeLabel(label)
                .maskedContext(context)
                .confidence(confidence)
                .build();
    }

    private ScanResult createScanResult(List<PiiEntity> entities) {
        return ScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .emittedAt("2024-01-15T10:00:00Z")
                .pageTitle("Test Page")
                .pageUrl("https://example.com/page")
                .attachmentName("doc.pdf")
                .attachmentUrl("https://example.com/attachment")
                .detectedEntities(entities)
                .build();
    }
}
