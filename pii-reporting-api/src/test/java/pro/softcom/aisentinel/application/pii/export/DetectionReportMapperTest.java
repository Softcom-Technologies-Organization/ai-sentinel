package pro.softcom.aisentinel.application.pii.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pro.softcom.aisentinel.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
    void Should_ReturnEmptyList_When_NoEntitiesToMap(
        ConfluenceContentScanResult confluenceContentScanResult) {
        // Given (provided by parameter)

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(
            confluenceContentScanResult);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_MapSingleEntity_When_OneEntityDetected")
    void Should_MapSingleEntity_When_OneEntityDetected() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createPiiEntity("EMAIL", "Email", "john@example.com", 0.95);
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult(List.of(entity));

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(
            confluenceContentScanResult);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().type()).isEqualTo("EMAIL");
        assertThat(result.getFirst().scanId()).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("provideMultipleEntities")
    @DisplayName("Should_MapAllEntities_When_MultipleEntitiesDetected")
    void Should_MapAllEntities_When_MultipleEntitiesDetected(List<DetectedPersonallyIdentifiableInformation> entities, int expectedCount) {
        // Given
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult(entities);

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(
            confluenceContentScanResult);

        // Then
        assertThat(result).hasSize(expectedCount);
    }

    @Test
    @DisplayName("Should_MapEntityFields_When_Mapping")
    void Should_MapEntityFields_When_Mapping() {
        // Given
        DetectedPersonallyIdentifiableInformation entity = createPiiEntity("EMAIL", "Email Label", "masked@example.com", 0.92);
        ConfluenceContentScanResult confluenceContentScanResult = createScanResult(List.of(entity));

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(
            confluenceContentScanResult);

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
        ConfluenceContentScanResult confluenceContentScanResult = ConfluenceContentScanResult.builder()
                .scanId("custom-scan-id")
                .spaceKey("CUSTOM-KEY")
                .emittedAt("2024-12-31T23:59:59Z")
                .pageTitle("Custom Page")
                .pageUrl("https://custom.com/page")
                .attachmentName("custom.doc")
                .attachmentUrl("https://custom.com/att")
                .detectedPIIList(List.of(createPiiEntity("EMAIL", "Email", "test@test.com", 0.9)))
                .build();

        // When
        List<DetectionReportEntry> result = mapper.toDetectionReportEntries(
            confluenceContentScanResult);

        // Then
        assertThat(result).hasSize(1);
        DetectionReportEntry entry = result.getFirst();
        assertThat(entry.scanId()).isNotBlank();
        assertThat(entry.spaceKey()).isNotBlank();
        assertThat(entry.emittedAt()).isNotBlank();
    }

    private static Stream<Arguments> provideEmptyScenarios() {
        return Stream.of(
                Arguments.of((ConfluenceContentScanResult) null),
                Arguments.of(
                    ConfluenceContentScanResult.builder().scanId("scan-123").spaceKey("TEST").detectedPIIList(null).build()),
                Arguments.of(
                    ConfluenceContentScanResult.builder().scanId("scan-123").spaceKey("TEST").detectedPIIList(List.of()).build())
        );
    }

    private static Stream<Arguments> provideMultipleEntities() {
        List<DetectedPersonallyIdentifiableInformation> scenario1 = List.of(
                createPiiEntity("EMAIL", "Email", "test@example.com", 0.9)
        );
        List<DetectedPersonallyIdentifiableInformation> scenario2 = List.of(
                createPiiEntity("EMAIL", "Email", "test@example.com", 0.9),
                createPiiEntity("PHONE", "Phone", "+33123456789", 0.85)
        );
        List<DetectedPersonallyIdentifiableInformation> scenario3 = List.of(
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

    private static DetectedPersonallyIdentifiableInformation createPiiEntity(String type, String label, String context, double confidence) {
        return DetectedPersonallyIdentifiableInformation.builder()
                .piiType(type)
                .piiTypeLabel(label)
                .maskedContext(context)
                .confidence(confidence)
                .build();
    }

    private ConfluenceContentScanResult createScanResult(List<DetectedPersonallyIdentifiableInformation> entities) {
        return ConfluenceContentScanResult.builder()
                .scanId("scan-123")
                .spaceKey("TEST")
                .emittedAt("2024-01-15T10:00:00Z")
                .pageTitle("Test Page")
                .pageUrl("https://example.com/page")
                .attachmentName("doc.pdf")
                .attachmentUrl("https://example.com/attachment")
                .detectedPIIList(entities)
                .build();
    }
}
