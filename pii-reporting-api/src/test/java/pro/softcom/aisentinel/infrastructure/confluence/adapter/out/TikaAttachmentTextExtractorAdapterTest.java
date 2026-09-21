package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;
import pro.softcom.aisentinel.infrastructure.document.config.TextQualityThresholds;
import pro.softcom.aisentinel.infrastructure.document.validator.TextQualityValidator;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TikaAttachmentTextExtractorAdapter.
 * <p>
 * Tests the complete flow: Tika extraction + TextQualityValidator validation.
 */
@DisplayName("TikaAttachmentTextExtractorAdapter - Integration")
class TikaAttachmentTextExtractorAdapterTest {

    private TikaAttachmentTextExtractorAdapter adapter;

    @Test
    void Should_RespectDeclaredCsvHeaderPresence_When_MimeTypeProvidesIt() {
        byte[] textOnly = "Unusual column;Other heading\nAlice Martin;Alpha\nJean Dupont;Beta".getBytes(StandardCharsets.UTF_8);
        var declared = adapter.extract(new AttachmentInfo("data.csv", "csv", "text/csv; header=present", "test"), textOnly)
                .orElseThrow();
        assertThat(declared.analysisText()).contains("Unusual column : Alice Martin", "Other heading : Beta");
        byte[] mixed = "Unusual column;Other heading\nAlice Martin;12345\nJean Dupont;67890".getBytes(StandardCharsets.UTF_8);
        var absent = adapter.extract(new AttachmentInfo("data.csv", "csv", "text/csv; header=absent", "test"), mixed)
                .orElseThrow();
        assertThat(absent.analysisText()).doesNotContain("Unusual column :", "Other heading :").contains("12345", "67890");
    }

    @Test
    void Should_UseNativeTableHeadersOnlyInsideTheirRange_When_XlsxContainsTextOnlyTables() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Text data");
            sheet.createRow(0).createCell(0).setCellValue("Introduction outside the table");
            Row header = sheet.createRow(2);
            header.createCell(1).setCellValue("Ungewöhnlicher Titel №2");
            header.createCell(2).setCellValue("Référence métier");
            Row first = sheet.createRow(3);
            first.createCell(1).setCellValue("Alice Martin");
            first.createCell(2).setCellValue("Alpha");
            Row second = sheet.createRow(4);
            second.createCell(1).setCellValue("Jean Dupont");
            second.createCell(2).setCellValue("Beta");
            sheet.createRow(5).createCell(1).setCellValue("Outside value");
            sheet.createTable(new AreaReference("B3:C5", SpreadsheetVersion.EXCEL2007));
            workbook.write(output);
            bytes = output.toByteArray();
        }

        var content = adapter.extract(createAttachment("native.xlsx", "xlsx"), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("Ungewöhnlicher Titel №2 : Alice Martin", "Référence métier : Beta")
                .doesNotContain("Ungewöhnlicher Titel №2 : Outside value");
        assertMapped(content, "Outside value");
    }

    @Test
    void Should_EnrichOriginalCoordinateColumns_When_ReadingReportedCsv() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/tables/correspondance_commune_arrondissement.csv")) {
            bytes = input.readAllBytes();
        }
        var content = adapter.extract(createAttachment("communes.csv", "csv"), bytes).orElseThrow();
        assertThat(content.analysisText()).contains("Coord_N : 1138667 | Coord_E : 2505047");
        assertThat(content.contextText().lines()).hasSize(303);
        assertMapped(content, "1138667");
        assertMapped(content, "2505047");
    }

    @BeforeEach
    void setUp() {
        // Given
        TextQualityThresholds thresholds = new TextQualityThresholds();
        TextQualityValidator validator = new TextQualityValidator(thresholds);
        adapter = new TikaAttachmentTextExtractorAdapter(validator);
    }

    @Test
    @DisplayName("Should_SupportExtractableAttachments_When_PdfFile")
    void Should_SupportExtractableAttachments_When_PdfFile() {
        // Given
        AttachmentInfo pdfAttachment = createAttachment("document.pdf", "pdf");

        // When
        boolean supports = adapter.supports(pdfAttachment);

        // Then
        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Should_NotSupportNonExtractableAttachments_When_ImageFile")
    void Should_NotSupportNonExtractableAttachments_When_ImageFile() {
        // Given
        AttachmentInfo imageAttachment = createAttachment("photo.jpg", "jpg");

        // When
        boolean supports = adapter.supports(imageAttachment);

        // Then
        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_BytesNull")
    void Should_ReturnEmpty_When_BytesNull() {
        // Given
        AttachmentInfo attachment = createAttachment("doc.pdf", "pdf");

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_BytesEmpty")
    void Should_ReturnEmpty_When_BytesEmpty() {
        // Given
        AttachmentInfo attachment = createAttachment("doc.pdf", "pdf");
        byte[] emptyBytes = new byte[0];

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, emptyBytes);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ExtractText_When_PlainTextContent")
    void Should_ExtractText_When_PlainTextContent() {
        // Given
        AttachmentInfo attachment = createAttachment("document.txt", "txt");
        String validText = "This is a valid text document with enough content to pass validation. " +
                "It contains proper spacing and sufficient alphanumeric characters.";
        byte[] bytes = validText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, bytes);

        // Then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(ExtractedContent::analysisText)
                .asString()
                .contains("valid text document");
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_TextTooShort")
    void Should_ReturnEmpty_When_TextTooShort() {
        // Given
        AttachmentInfo attachment = createAttachment("short.txt", "txt");
        String shortText = "Too short";
        byte[] bytes = shortText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, bytes);

        // Then - Validation should reject it
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_TextHasLowAlphanumericRatio")
    void Should_ReturnEmpty_When_TextHasLowAlphanumericRatio() {
        // Given
        AttachmentInfo attachment = createAttachment("corrupted.txt", "txt");
        String corruptedText = "!!!@@@###$$$%%%^^^&&&***((()))____----====++++||||\\\\\\///...";
        byte[] bytes = corruptedText.getBytes(StandardCharsets.UTF_8);

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, bytes);

        // Then - Validation should reject it
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_ExtractionFails")
    void Should_ReturnEmpty_When_ExtractionFails() {
        // Given
        AttachmentInfo attachment = createAttachment("invalid.pdf", "pdf");
        byte[] invalidBytes = "This is not a valid PDF".getBytes(StandardCharsets.UTF_8);

        // When
        Optional<ExtractedContent> result = adapter.extract(attachment, invalidBytes);

        // Then - Tika should fail to parse and return empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_BeAnnotatedAsComponent_When_ClassDefined")
    void Should_BeAnnotatedAsComponent_When_ClassDefined() {
        // Given & When & Then
        assertThat(TikaAttachmentTextExtractorAdapter.class)
                .hasAnnotation(org.springframework.stereotype.Component.class);
    }

    @Test
    @DisplayName("Should_ImplementStrategy_When_ClassDefined")
    void Should_ImplementStrategy_When_ClassDefined() {
        // Given & When & Then
        assertThat(AttachmentTextExtractionStrategy.class)
                .isAssignableFrom(TikaAttachmentTextExtractorAdapter.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void Should_AssociateHeadersWithValues_When_WorkbookHasEmptyMiddleCell(String extension) throws Exception {
        byte[] bytes;
        try (Workbook workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employees");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Nom");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Port");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Alice Martin");
            row.createCell(2).setCellValue("587");
            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("Jean Dupont");
            second.createCell(1).setCellValue("jean@example.test");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        ExtractedContent content = adapter.extract(createAttachment("contacts." + extension, extension), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("Nom : Alice Martin", "Port : 587", "Email : jean@example.test")
                .doesNotContain("Email : 587");
        assertThat(content.contextText()).contains("Nom", "Email", "Port", "Alice Martin", "587");
        assertMapped(content, "jean@example.test");
    }

    @Test
    void Should_AssociateCsvHeaders_When_BomSemicolonQuotesAndMultilineValues() {
        byte[] bytes = ("\ufeffNom;Email;Port\r\n\"Durand; Jean\";jean@example.test;587\r\n"
                + "\"Martin\nAlice\";alice@example.test;443").getBytes(StandardCharsets.UTF_8);

        ExtractedContent content = adapter.extract(createAttachment("contacts.csv", "csv"), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("Nom : Durand; Jean", "Port : 587", "Email : alice@example.test");
        assertThat(content.contextText()).contains("Durand; Jean", "Martin", "Alice").doesNotContain("\ufeff");
        assertMapped(content, "alice@example.test");
    }

    @Test
    void Should_PreserveHeaderlessSheet_When_AnotherSheetHasHeaders() throws Exception {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet known = workbook.createSheet("Contacts with known headers");
            known.createRow(0).createCell(0).setCellValue("Email");
            known.createRow(1).createCell(0).setCellValue("alice@example.test");
            Sheet unknown = workbook.createSheet("Rows with no known header");
            unknown.createRow(0).createCell(0).setCellValue("preserve-first@example.test");
            unknown.createRow(1).createCell(0).setCellValue("preserve-last@example.test");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        ExtractedContent content = adapter.extract(createAttachment("contacts.xlsx", "xlsx"), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("Email : alice@example.test", "preserve-first@example.test", "preserve-last@example.test");
        assertMapped(content, "preserve-first@example.test");
    }

    @Test
    void Should_AssociateOdsHeaders_When_ContentIsZippedOpenDocument() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                <office:body><office:spreadsheet><table:table table:name="Contacts">
                <table:table-row><table:table-cell><text:p>Nom</text:p></table:table-cell>
                <table:table-cell><text:p>Email</text:p></table:table-cell></table:table-row>
                <table:table-row><table:table-cell><text:p>Alice Martin</text:p></table:table-cell>
                <table:table-cell><text:p>alice@example.test</text:p></table:table-cell></table:table-row>
                <table:table-row><table:table-cell><text:p>Jean Dupont</text:p></table:table-cell>
                <table:table-cell><text:p>jean@example.test</text:p></table:table-cell></table:table-row>
                </table:table></office:spreadsheet></office:body></office:document-content>
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ExtractedContent content = adapter.extract(createAttachment("contacts.ods", "ods"), output.toByteArray()).orElseThrow();

        assertThat(content.analysisText()).contains("Nom : Alice Martin", "Email : jean@example.test");
        assertMapped(content, "alice@example.test");
    }

    private void assertMapped(ExtractedContent content, String value) {
        int start = content.analysisText().indexOf(value);
        assertThat(content.segments()).anySatisfy(segment -> {
            assertThat(start).isGreaterThanOrEqualTo(segment.analysisStart());
            assertThat(start + value.length()).isLessThanOrEqualTo(segment.analysisStart() + segment.length());
            int originalStart = segment.contextStart() + start - segment.analysisStart();
            assertThat(content.contextText().substring(originalStart, originalStart + value.length())).isEqualTo(value);
        });
    }

    @Test
    void Should_KeepAllText_When_StructuredOutputExceedsLimit() {
        String text = "Start marker " + "ordinary text ".repeat(160_000) + " End marker";
        byte[] bytes = ("<html><body><p>" + text + "</p></body></html>").getBytes(StandardCharsets.UTF_8);

        ExtractedContent content = adapter.extract(createAttachment("large.html", "html"), bytes).orElseThrow();

        assertThat(content.analysisText()).isEqualTo(content.contextText()).contains(text);
    }

    @Test
    void Should_NotInferHtmlHeaders_When_FirstRowContainsColumnNames() {
        byte[] bytes = ("<html><body><table><tr><td>Nom</td><td>Email</td></tr>"
                + "<tr><td>Alice Martin</td><td>alice@example.test</td></tr>"
                + "<tr><td>Jean Dupont</td><td>jean@example.test</td></tr></table></body></html>")
                .getBytes(StandardCharsets.UTF_8);

        ExtractedContent content = adapter.extract(createAttachment("table.html", "html"), bytes).orElseThrow();

        assertThat(content.analysisText()).doesNotContain("Nom :", "Email :").contains("alice@example.test");
    }

    @Test
    void Should_ReadExplicitHtmlHeaders_When_TikaWouldClassifyShortHtmlAsText() {
        byte[] bytes = ("<html><body><table><tr><th>Nom</th><th>Email</th></tr>"
                + "<tr><td>Alice Martin</td><td>alice@example.test</td></tr>"
                + "<tr><td>Jean Dupont</td><td>jean@example.test</td></tr></table></body></html>")
                .getBytes(StandardCharsets.UTF_8);

        ExtractedContent content = adapter.extract(createAttachment("table.html", "html"), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("Nom : Alice Martin", "Email : jean@example.test");
    }

    @Test
    void Should_NotTreatMergedTitleAsColumnHeader_When_LaterRowsAreWider() throws Exception {
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Contact details for employees");
            sheet.createRow(0).createCell(0).setCellValue("Service");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("Email");
            header.createCell(1).setCellValue("Téléphone");
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("alice@example.test");
            row.createCell(1).setCellValue("+41 79 123 45 67");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        ExtractedContent content = adapter.extract(createAttachment("contacts.xlsx", "xlsx"), bytes).orElseThrow();

        assertThat(content.analysisText()).contains("alice@example.test", "+41 79 123 45 67").doesNotContain("Service :");
    }

    private AttachmentInfo createAttachment(String name, String extension) {
        return new AttachmentInfo(name, extension, "application/octet-stream", "http://test");
    }
}
