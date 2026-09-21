package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Optional;

final class CsvAttachmentTableParser {

    private static final int MAX_CELLS = 100_000;

    private CsvAttachmentTableParser() {
    }

    static Optional<Document> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Document result = null;
        for (char delimiter : new char[]{',', ';', '\t'}) {
            Document candidate = parse(text, delimiter);
            if (candidate != null) {
                if (result != null) {
                    return Optional.empty();
                }
                result = candidate;
            }
        }
        return Optional.ofNullable(result);
    }

    private static Document parse(String text, char delimiter) {
        CSVFormat format = CSVFormat.EXCEL.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).get();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            Document document = Document.createShell("");
            Element table = document.body().appendElement("table");
            int width = 0;
            int cells = 0;
            for (CSVRecord record : parser) {
                width = width == 0 ? record.size() : width;
                cells += record.size();
                if (width < 2 || record.size() != width || cells > MAX_CELLS) {
                    return null;
                }
                Element row = table.appendElement("tr");
                record.forEach(value -> row.appendElement("td").text(value));
            }
            return table.childrenSize() >= 2 ? document : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
