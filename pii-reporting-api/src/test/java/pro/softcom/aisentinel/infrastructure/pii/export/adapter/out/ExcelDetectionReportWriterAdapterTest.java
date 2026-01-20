package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import pro.softcom.aisentinel.domain.pii.export.ExportContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Excel file and sheet name sanitization tests")
class ExcelDetectionReportWriterAdapterTest {

    @TempDir
    Path tempDir;

    private ExcelDetectionReportWriterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ExcelDetectionReportWriterAdapter();
        ReflectionTestUtils.setField(adapter, "exportDirectory", tempDir.toString());
    }

    @ParameterizedTest(name = "Should create valid file name with report name: ''{0}''")
    @MethodSource("provideInvalidReportNamesForFileName")
    @DisplayName("Should_CreateValidFileName_When_ReportNameContainsInvalidCharacters")
    void Should_CreateValidFileName_When_ReportNameContainsInvalidCharacters(String reportName, String[] forbiddenChars) throws IOException {
        // Given
        ExportContext context = createExportContext(reportName);

        // When
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            // Then
            try (var filesStream = Files.list(tempDir)) {
                List<Path> files = filesStream
                        .filter(p -> p.toString().endsWith(".xlsx"))
                        .toList();

                assertThat(files).hasSize(1);
                String fileName = files.getFirst().getFileName().toString();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(fileName)
                            .as("File name must not contain forbidden characters")
                            .doesNotContain(forbiddenChars);
                    softly.assertThat(fileName)
                            .as("File name must end with .xlsx")
                            .endsWith(".xlsx");
                    softly.assertThat(fileName)
                            .as("File name must not be empty")
                            .isNotBlank();
                });
            }
        }
    }

    private static Stream<Arguments> provideInvalidReportNamesForFileName() {
        return Stream.of(
                Arguments.of("Test<>:\"/\\|?*Space", new String[]{"<", ">", ":", "\"", "/", "\\", "|", "?", "*"}),
                Arguments.of("Test\u0000\u001FSpace", new String[]{"\u0000", "\u001F"})
        );
    }

    @Test
    @DisplayName("Should_SanitizeFileName_When_ReportNameContainsOnlyForbiddenCharacters")
    void Should_SanitizeFileName_When_ReportNameContainsOnlyForbiddenCharacters() throws IOException {
        // Given
        ExportContext context = createExportContext("<>:\"/\\|?*");

        // When
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            // Then
            try (var filesStream = Files.list(tempDir)) {
                List<Path> files = filesStream
                        .filter(p -> p.toString().endsWith(".xlsx"))
                        .toList();

                assertThat(files).hasSize(1);
                String fileName = files.getFirst().getFileName().toString();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(fileName)
                            .as("File name must not contain forbidden characters")
                            .doesNotContain("<", ">", ":", "\"", "/", "\\", "|", "?", "*");
                    softly.assertThat(fileName)
                            .as("File name must end with .xlsx")
                            .endsWith(".xlsx");
                    softly.assertThat(fileName)
                            .as("File name must not be empty")
                            .isNotBlank();
                });
            }
        }
    }

    @Test
    @DisplayName("Should_TruncateFileName_When_ReportNameIsTooLong")
    void Should_TruncateFileName_When_ReportNameIsTooLong() throws IOException {
        // Given
        String longName = "A".repeat(300);
        ExportContext context = createExportContext(longName);

        // When
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            // Then
            try (var files = Files.list(tempDir)) {
                var filteredFiles = files.filter(p -> p.toString().endsWith(".xlsx"))
                        .toList();

                assertThat(filteredFiles).hasSize(1);
                String fileName = filteredFiles.getFirst().getFileName().toString();
                assertThat(fileName.length()).isLessThanOrEqualTo(255);
            }
        }
    }

    @Test
    @DisplayName("Should_PreserveValidCharacters_When_ReportNameIsValid")
    void Should_PreserveValidCharacters_When_ReportNameIsValid() throws IOException {
        // Given
        ExportContext context = createExportContext("Valid-Space_Name123");

        // When
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            // Then
            try (var filesStream = Files.list(tempDir)) {
                List<Path> files = filesStream
                        .filter(p -> p.toString().endsWith(".xlsx"))
                        .toList();

                assertThat(files).hasSize(1);
                assertThat(files.getFirst().getFileName().toString()).hasToString("Valid-Space_Name123.xlsx");
            }
        }
    }

    @ParameterizedTest(name = "Should handle Excel sheet name: ''{1}''")
    @MethodSource("provideReportNamesForSheetValidation")
    @DisplayName("Should_CreateValidSheetName_When_ReportNameHasSpecialRequirements")
    void Should_CreateValidSheetName_When_ReportNameHasSpecialRequirements(String reportName, String description) throws IOException {
        // Given
        ExportContext context = createExportContext(reportName);

        // When & Then - Should not throw exception
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            assertThat(session).isNotNull();
        }
    }

    private static Stream<Arguments> provideReportNamesForSheetValidation() {
        return Stream.of(
                // Apache POI WorkbookUtil forbids: \ / ? * [ ]
                Arguments.of("Test[Sheet]:Name/With?Invalid*Chars\\", "Forbidden Excel characters"),
                // "History" is a reserved Excel name
                Arguments.of("History", "Reserved Excel name"),
                // Excel limits sheet names to 31 characters
                Arguments.of("A".repeat(50), "Exceeds 31 characters")
        );
    }

    @Test
    @DisplayName("Should_HandleSpacesAndSpecialChars_When_ReportNameHasMixedContent")
    void Should_HandleSpacesAndSpecialChars_When_ReportNameHasMixedContent() throws IOException {
        // Given
        ExportContext context = createExportContext("  Test Space : 2024  ");

        // When
        try (var session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            // Then
            try (var filesStream = Files.list(tempDir)) {
                List<Path> files = filesStream
                        .filter(p -> p.toString().endsWith(".xlsx"))
                        .toList();

                assertThat(files).hasSize(1);
                String fileName = files.getFirst().getFileName().toString();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(fileName)
                            .as("File name must handle spaces and special characters")
                            .isNotBlank();
                    softly.assertThat(fileName)
                            .as("File name must not contain colons")
                            .doesNotContain(":");
                });
            }
        }
    }

    private ExportContext createExportContext(String reportName) {
        return ExportContext.builder()
                .reportName(reportName)
                .reportIdentifier("TEST")
                .sourceUrl("https://example.com/source/TEST")
                .contacts(List.of())
                .additionalMetadata(Map.of())
                .build();
    }
}
