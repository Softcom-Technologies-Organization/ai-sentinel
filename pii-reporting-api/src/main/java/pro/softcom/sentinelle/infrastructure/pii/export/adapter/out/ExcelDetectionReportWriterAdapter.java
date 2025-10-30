package pro.softcom.sentinelle.infrastructure.pii.export.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.tika.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.export.dto.DetectionReportEntry;
import pro.softcom.sentinelle.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.sentinelle.domain.pii.export.ExportContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExcelDetectionReportWriterAdapter implements WriteDetectionReportPort {
    @Value("${pii-reporting-api.findings-export-directory}")
    private String exportDirectory;

    @Override
    public ReportSession openReportSession(String scanId, ExportContext exportContext) throws IOException {
        return new ExcelSession(scanId, exportContext, exportDirectory);
    }

    private static class ExcelSession implements ReportSession {
        private final String scanId;
        private final ExportContext exportContext;
        private final SXSSFWorkbook workbook;
        private final Path reportPath;
        private OutputStream outputStream;
        private Sheet summarySheet;
        private Sheet detectionsSheet;
        private int detectionsSheetRowIndex = 0;

        private CellStyle boldStyle;
        private CellStyle dateStyle;
        private CellStyle urlStyle;
        private CellStyle decimalStyle;

        public ExcelSession(String scanId, ExportContext exportContext, String exportDirectory) {
            this.scanId = scanId;
            this.exportContext = exportContext;
            this.workbook = new SXSSFWorkbook();

            String safeFileName = sanitizeForFileName(exportContext.reportName(), ".xlsx");
            this.reportPath = Path.of(exportDirectory, safeFileName);
        }

        @Override
        public void startReport() throws IOException {
            Files.createDirectories(this.reportPath.getParent());
            this.outputStream = Files.newOutputStream(this.reportPath);

            createStyles();
            prepareDetectionsSheet();
            populateSummarySheet();
        }

        @Override
        public void finishReport() throws IOException {
            final int EXCEL_COLUMN_WIDTH_UNIT = 256;
            final int DEFAULT_COLUMN_CHAR_WIDTH = 20;

            // Best effort columns autosize
            summarySheet.setColumnWidth(0, DEFAULT_COLUMN_CHAR_WIDTH * EXCEL_COLUMN_WIDTH_UNIT);
            summarySheet.setColumnWidth(1, DEFAULT_COLUMN_CHAR_WIDTH * EXCEL_COLUMN_WIDTH_UNIT);
            List.of(
                    ExportColumn.EMITTED_AT,
                    ExportColumn.PAGE_TITLE,
                    ExportColumn.CONFIDENCE_SCORE,
                    ExportColumn.PII_TYPE
            ).forEach(d -> detectionsSheet.setColumnWidth(d.position(), DEFAULT_COLUMN_CHAR_WIDTH * EXCEL_COLUMN_WIDTH_UNIT));

            workbook.write(outputStream);
            outputStream.flush();
        }

        @Override
        public void writeReportEntry(DetectionReportEntry detectionReportEntry) {
            Row row = detectionsSheet.createRow(detectionsSheetRowIndex++);
            populateDateField(row, ExportColumn.EMITTED_AT.position(), detectionReportEntry.emittedAt());
            populateTextField(row, ExportColumn.PAGE_TITLE.position(), detectionReportEntry.pageTitle());
            populateUrlField(row, ExportColumn.PAGE_URL.position(), detectionReportEntry.pageUrl());
            populateTextField(row, ExportColumn.DOCUMENT_NAME.position(), detectionReportEntry.attachmentName());
            populateUrlField(row, ExportColumn.DOCUMENT_URL.position(), detectionReportEntry.attachmentUrl());
            populateTextField(row, ExportColumn.PII_TYPE.position(), detectionReportEntry.typeLabel());
            populateTextField(row, ExportColumn.PII_CONTEXT.position(), detectionReportEntry.maskedContext());

            Cell scoreCell = row.createCell(ExportColumn.CONFIDENCE_SCORE.position());
            scoreCell.setCellValue(detectionReportEntry.confidenceScore());
            scoreCell.setCellStyle(decimalStyle);
        }

        private void populateSummarySheet() {
            summarySheet = workbook.createSheet("Space Summary");

            AtomicInteger rowIndex = new AtomicInteger();

            var rowSpace = summarySheet.createRow(rowIndex.getAndIncrement());
            populateTextField(rowSpace, 0, "Name", true);
            populateTextField(rowSpace, 1, exportContext.reportName());

            var rowUrl = summarySheet.createRow(rowIndex.getAndIncrement());
            populateTextField(rowUrl, 0, "URL", true);
            populateUrlField(rowUrl, 1, exportContext.sourceUrl());

            var rowScanId = summarySheet.createRow(rowIndex.getAndIncrement());
            populateTextField(rowScanId, 0, "ScanID", true);
            populateTextField(rowScanId, 1, scanId);

            var rowContact = summarySheet.createRow(rowIndex.getAndIncrement());
            populateTextField(rowContact, 0, "Contacts", true);
            for(var contact : exportContext.contacts()) {
                rowContact.createCell(1).setCellValue(contact.displayName());
                rowContact.createCell(2).setCellValue(contact.email());
                rowContact = summarySheet.createRow(rowIndex.getAndIncrement());
            }
        }

        private void prepareDetectionsSheet() {
            detectionsSheet = workbook.createSheet("Detection Report");
            Row header = detectionsSheet.createRow(detectionsSheetRowIndex++);
            for (ExportColumn exportColumn : ExportColumn.values()) {
                populateTextField(header, exportColumn.position(), exportColumn.header(), true);
            }
        }

        private void populateTextField(Row row, int index, String rawValue, boolean bold) {
            var cell = row.createCell(index);
            cell.setCellValue(rawValue);
            if (bold) {
                cell.setCellStyle(boldStyle);
            }
        }

        private void populateTextField(Row row, int index, String rawValue) {
            populateTextField(row, index, rawValue, false);
        }

        private void populateDateField(Row row, int index, String rawValue) {
            var dateCell = row.createCell(index);
            try {
                Instant instant = Instant.parse(rawValue);
                dateCell.setCellValue(Date.from(instant));
                dateCell.setCellStyle(dateStyle);
            } catch (DateTimeParseException _) {
                log.warn("Invalid date format: {}, using raw value", rawValue);
                dateCell.setCellValue(rawValue); // fallback
            }
        }

        private void populateUrlField(Row row, int index, String rawValue) {
            Cell urlCell = row.createCell(index);
            try {
                urlCell.setCellValue(rawValue);
                urlCell.setCellStyle(urlStyle);

                Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
                if (StringUtils.isNotBlank(rawValue)) {
                    link.setAddress(rawValue);
                    urlCell.setHyperlink(link);
                }
            } catch (IllegalArgumentException _) {
                log.warn("Invalid URL format: {}, using raw value", rawValue);
                urlCell.setCellValue(rawValue); // fallback
            }
        }

        @Override
        public void close() throws IOException {
            try {
                workbook.close();
            } finally {
                outputStream.close();
            }
        }

        private void createStyles() {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle = workbook.createCellStyle();
            boldStyle.setFont(boldFont);

            // Date cell (YYYY-MM-DD HH:mm:ss)
            DataFormat dataFormat = workbook.createDataFormat();
            dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("dd.mm.yyyy hh:mm"));

            // Page URL cell
            Font linkFont = workbook.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            urlStyle = workbook.createCellStyle();
            urlStyle.setFont(linkFont);

            // Score cell
            decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(dataFormat.getFormat("0.0000"));
        }

        /**
         * Sanitizes report name for use as a file name.
         * Ensures cross-platform compatibility (Windows, Linux, macOS).
         * File name limit is 255 characters including extension on all major OS.
         *
         * @param reportName the report name to sanitize
         * @param extension the file extension (e.g., ".xlsx")
         * @return sanitized file name with extension, compatible with all operating systems
         */
        private String sanitizeForFileName(String reportName, String extension) {
            String baseName = FilenameUtils.getName(reportName);
            String sanitized = baseName.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").trim();

            if (sanitized.isEmpty()) {
                sanitized = "detections-report";
            }

            int maxLength = 255 - extension.length();
            if (sanitized.length() > maxLength) {
                sanitized = sanitized.substring(0, maxLength);
            }

            return sanitized + extension;
        }

        private enum ExportColumn {
            EMITTED_AT(0, "emittedAt"),
            PAGE_TITLE(1, "Page Title"),
            PAGE_URL(2, "Page Url"),
            DOCUMENT_NAME(3, "Document Title"),
            DOCUMENT_URL(4, "Document Url"),
            CONFIDENCE_SCORE(5, "Confidence Score"),
            PII_TYPE(6, "PII Type"),
            PII_CONTEXT(7, "PII Context");

            private final int position;
            private final String header;

            ExportColumn(int position, String header) {
                this.position = position;
                this.header = header;
            }

            public int position() {
                return position;
            }

            public String header() {
                return header;
            }
        }
    }
}
