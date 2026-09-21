package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class SpreadsheetAttachmentTableParser {

    private static final int MAX_CELLS = 100_000;
    private static final int MAX_TEXT_CHARACTERS = 2_000_000;

    private SpreadsheetAttachmentTableParser() {
    }

    static Document parse(InputStream input) throws IOException {
        try (var workbook = WorkbookFactory.create(input)) {
            Document document = Document.createShell("");
            var formatter = new DataFormatter(Locale.ROOT);
            formatter.setUseCachedValuesForFormulaCells(true);
            long cells = 0;
            int textCharacters = 0;
            for (Sheet sheet : workbook) {
                int width = width(sheet);
                cells += ((long) sheet.getLastRowNum() + 1) * width;
                if (cells > MAX_CELLS) {
                    throw new IOException("Spreadsheet exceeds the structured cell limit");
                }
                document.body().appendElement("h1").text(sheet.getSheetName());
                Element table = document.body().appendElement("table");
                textCharacters += appendRows(table, sheet, formatter);
                if (textCharacters > MAX_TEXT_CHARACTERS) {
                    throw new IOException("Spreadsheet exceeds the structured text limit");
                }
                if (sheet instanceof XSSFSheet xssf && !xssf.getTables().isEmpty()) {
                    table.select("td").attr("headers", "");
                    for (XSSFTable declared : xssf.getTables()) {
                        markHeaders(table, declared);
                    }
                }
            }
            return document;
        }
    }

    private static int width(Sheet sheet) {
        int width = 0;
        for (Row row : sheet) {
            width = Math.max(width, row.getLastCellNum());
        }
        return width;
    }

    private static int appendRows(Element table, Sheet sheet, DataFormatter formatter) throws IOException {
        int width = width(sheet);
        int characters = 0;
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            Row source = sheet.getRow(index);
            Element row = table.appendElement("tr");
            for (int column = 0; column < width; column++) {
                String value = source == null ? "" : formatter.formatCellValue(source.getCell(column));
                characters += value.length();
                if (characters > MAX_TEXT_CHARACTERS) {
                    throw new IOException("Spreadsheet exceeds the structured text limit");
                }
                row.appendElement("td").text(value);
            }
        }
        return characters;
    }

    private static void markHeaders(Element table, XSSFTable declared) {
        int startRow = declared.getStartRowIndex();
        int endRow = Math.min(declared.getEndRowIndex(), table.childrenSize() - 1);
        int headerCount = declared.getHeaderRowCount();
        if (headerCount <= 0 || startRow < 0 || startRow > endRow || headerCount > endRow - startRow + 1
                || declared.getStartColIndex() < 0 || declared.getEndColIndex() < declared.getStartColIndex()
                || declared.getEndColIndex() >= table.child(startRow).childrenSize()) {
            return;
        }
        int headerEnd = startRow + headerCount;
        for (int column = declared.getStartColIndex(); column <= declared.getEndColIndex(); column++) {
            int position = column;
            String ids = IntStream.range(startRow, headerEnd)
                    .mapToObj(row -> "r" + row + "c" + position).collect(Collectors.joining(" "));
            for (int row = startRow; row <= endRow; row++) {
                Element cell = table.child(row).child(column);
                if (row < headerEnd) {
                    cell.tagName("th").attr("scope", "col").attr("id", "r" + row + "c" + column);
                } else {
                    cell.attr("headers", ids);
                }
            }
        }
    }
}
