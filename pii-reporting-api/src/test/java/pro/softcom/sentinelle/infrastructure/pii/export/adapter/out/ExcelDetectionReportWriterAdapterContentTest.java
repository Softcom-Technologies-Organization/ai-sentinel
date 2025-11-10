package pro.softcom.sentinelle.infrastructure.pii.export.adapter.out;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import pro.softcom.sentinelle.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.sentinelle.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.sentinelle.domain.pii.export.DataSourceContact;
import pro.softcom.sentinelle.domain.pii.export.ExportContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Excel report content and data writing tests")
class ExcelDetectionReportWriterAdapterContentTest {

    @TempDir
    Path tempDir;

    private ExcelDetectionReportWriterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ExcelDetectionReportWriterAdapter();
        ReflectionTestUtils.setField(adapter, "exportDirectory", tempDir.toString());
    }

    @Test
    @DisplayName("Should_CreateValidExcelFileWithBothSheets_When_ReportCompletes")
    void Should_CreateValidExcelFileWithBothSheets_When_ReportCompletes() throws IOException {
        // Given
        ExportContext context = createExportContext(List.of());

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-123", context)) {
            session.startReport();
            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            assertThat(workbook.getSheet("Space Summary")).isNotNull();
            assertThat(workbook.getSheet("Detection Report")).isNotNull();
        }
    }

    @ParameterizedTest
    @MethodSource("provideContactScenarios")
    @DisplayName("Should_PopulateSummarySheet_When_ContactsProvided")
    void Should_PopulateSummarySheet_When_ContactsProvided(List<DataSourceContact> contacts) throws IOException {
        // Given
        ExportContext context = createExportContext(contacts);

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-456", context)) {
            session.startReport();
            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            Sheet summarySheet = workbook.getSheet("Space Summary");
            assertThat(summarySheet).isNotNull();
            assertThat(summarySheet.getRow(0)).isNotNull();
        }
    }

    private static Stream<Arguments> provideContactScenarios() {
        List<DataSourceContact> scenario1 = List.of();
        List<DataSourceContact> scenario2 = List.of(
                DataSourceContact.builder()
                        .displayName("John Doe")
                        .email("john@example.com")
                        .build()
        );
        List<DataSourceContact> scenario3 = List.of(
                DataSourceContact.builder()
                        .displayName("John Doe")
                        .email("john@example.com")
                        .build(),
                DataSourceContact.builder()
                        .displayName("Jane Smith")
                        .email("jane@example.com")
                        .build()
        );

        return Stream.of(
                Arguments.of(scenario1),
                Arguments.of(scenario2),
                Arguments.of(scenario3)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1",
            "3"
    })
    @DisplayName("Should_WriteAllEntries_When_MultipleEntriesProvided")
    void Should_WriteAllEntries_When_MultipleEntriesProvided(int count) throws IOException {
        // Given
        ExportContext context = createExportContext(List.of());

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-multi", context)) {
            session.startReport();

            for (int i = 1; i <= count; i++) {
                DetectionReportEntry entry = createDetectionEntry(
                        "2024-01-15T10:00:00Z",
                        "Page " + i,
                        "EMAIL"
                );
                session.writeReportEntry(entry);
            }

            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            Sheet detectionsSheet = workbook.getSheet("Detection Report");
            assertThat(detectionsSheet.getLastRowNum()).isEqualTo(count);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2024-01-15T10:30:00Z, true",
            "2024-12-31T23:59:59Z, true",
            "invalid-date-format, false"
    })
    @DisplayName("Should_FormatDateField_When_ValidOrInvalidDateProvided")
    void Should_FormatDateField_When_ValidOrInvalidDateProvided(String dateValue, boolean shouldBeNumeric) throws IOException {
        // Given
        ExportContext context = createExportContext(List.of());
        DetectionReportEntry entry = createDetectionEntry(dateValue, "Test Page", "EMAIL");

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-date", context)) {
            session.startReport();
            session.writeReportEntry(entry);
            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            Sheet detectionsSheet = workbook.getSheet("Detection Report");
            Cell dateCell = detectionsSheet.getRow(1).getCell(0);

            if (shouldBeNumeric) {
                assertThat(dateCell.getCellType()).isEqualTo(CellType.NUMERIC);
            } else {
                assertThat(dateCell.getCellType()).isEqualTo(CellType.STRING);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com/page, true",
            "not a valid url, false"
    })
    @DisplayName("Should_CreateHyperlink_When_ValidURLProvided")
    void Should_CreateHyperlink_When_ValidURLProvided(String url, boolean shouldHaveHyperlink) throws IOException {
        // Given
        ExportContext context = createExportContext(List.of());
        DetectionReportEntry entry = DetectionReportEntry.builder()
                .scanId("scan-url")
                .spaceKey("TEST")
                .emittedAt("2024-01-15T10:00:00Z")
                .pageTitle("Test Page")
                .pageUrl(url)
                .attachmentName("")
                .attachmentUrl("")
                .maskedContext("Context")
                .type("EMAIL")
                .typeLabel("Email")
                .confidenceScore(0.85)
                .build();

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-url", context)) {
            session.startReport();
            session.writeReportEntry(entry);
            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            Sheet detectionsSheet = workbook.getSheet("Detection Report");
            Cell urlCell = detectionsSheet.getRow(1).getCell(2);

            assertThat(urlCell.getStringCellValue()).isNotBlank();
            if (shouldHaveHyperlink) {
                assertThat(urlCell.getHyperlink()).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("Should_FormatScoreAndApplyStyles_When_CreatingDetectionSheet")
    void Should_FormatScoreAndApplyStyles_When_CreatingDetectionSheet() throws IOException {
        // Given
        ExportContext context = createExportContext(List.of());
        DetectionReportEntry entry = createDetectionEntry("2024-01-15T10:00:00Z", "Test", "EMAIL");

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan-style", context)) {
            session.startReport();
            session.writeReportEntry(entry);
            session.finishReport();
        }

        // Then
        try (Workbook workbook = openWorkbook()) {
            Sheet detectionsSheet = workbook.getSheet("Detection Report");
            Row headerRow = detectionsSheet.getRow(0);
            Cell scoreCell = detectionsSheet.getRow(1).getCell(5);

            assertThat(headerRow).isNotNull();
            assertThat(scoreCell.getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    @DisplayName("Should_CreateDirectories_When_ExportDirectoryMissing")
    void Should_CreateDirectories_When_ExportDirectoryMissing() throws IOException {
        // Given
        Path nonExistentDir = tempDir.resolve("nested/directory");
        ReflectionTestUtils.setField(adapter, "exportDirectory", nonExistentDir.toString());

        // When
        try (WriteDetectionReportPort.ReportSession session = adapter.openReportSession("scan", createExportContext(List.of()))) {
            session.startReport();
            session.finishReport();
        }

        // Then
        assertThat(nonExistentDir).exists().isDirectory();
    }

    private ExportContext createExportContext(List<DataSourceContact> contacts) {
        return ExportContext.builder()
                .reportName("Test Space")
                .reportIdentifier("TEST")
                .sourceUrl("https://example.com/space/TEST")
                .contacts(contacts)
                .additionalMetadata(Map.of())
                .build();
    }

    private DetectionReportEntry createDetectionEntry(String emittedAt, String pageTitle, String piiType) {
        return DetectionReportEntry.builder()
                .scanId("scan-test")
                .spaceKey("TEST")
                .emittedAt(emittedAt)
                .pageTitle(pageTitle)
                .pageUrl("https://example.com/page/123")
                .attachmentName("test.pdf")
                .attachmentUrl("https://example.com/attachment/456")
                .maskedContext("PII context: ***")
                .type(piiType)
                .typeLabel(piiType + " Label")
                .confidenceScore(0.9567)
                .build();
    }

    private Workbook openWorkbook() throws IOException {
        try (var filesStream = Files.list(tempDir)) {
            List<Path> files = filesStream.filter(p -> p.toString().endsWith(".xlsx")).toList();
            assertThat(files).hasSize(1);
            return new XSSFWorkbook(new FileInputStream(files.get(0).toFile()));
        }
    }
}
