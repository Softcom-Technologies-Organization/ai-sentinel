package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.google.re2j.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.stream.IntStream;

final class TabularAttachmentHeaders {

    private static final int SAMPLE_ROWS = 20;
    private static final List<Pattern> VALUE_SHAPES = List.of(
            Pattern.compile("[+-]?\\d+(?:[.,]\\d+)?(?:[eE][+-]?\\d+)?"),
            Pattern.compile("(?:\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{2}[./-]\\d{2}[./-]\\d{4})"),
            Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"));

    private TabularAttachmentHeaders() {
    }

    static void mark(Document document) {
        mark(document, "");
    }

    static void mark(Document document, String declaration) {
        if (document != null && !"absent".equalsIgnoreCase(declaration)) {
            document.select("table").forEach(table -> markTable(table, declaration));
        }
    }

    private static void markTable(Element table, String declaration) {
        if (!table.select("th, thead, [headers]").isEmpty()) {
            return;
        }
        List<Element> rows = table.select("tr").stream()
                .filter(row -> row.closest("table") == table && !row.text().isBlank()).toList();
        if (!rows.isEmpty() && ("present".equalsIgnoreCase(declaration) || (rows.size() >= 2 && isHeader(rows)))) {
            rows.getFirst().children().forEach(cell -> cell.tagName("th").attr("scope", "col"));
        }
    }

    private static boolean isHeader(List<Element> rows) {
        Elements cells = rows.getFirst().children();
        if (cells.isEmpty() || rows.stream().anyMatch(row -> row.childrenSize() > cells.size())
                || rows.stream().anyMatch(row -> !row.select("[colspan], [rowspan], table").isEmpty())) {
            return false;
        }
        List<String> labels = cells.stream().map(Element::text).toList();
        return labels.stream().filter(label -> !label.isBlank()).allMatch(TabularAttachmentHeaders::isLabel)
                && IntStream.range(0, cells.size()).anyMatch(column -> hasValueColumn(rows, column));
    }

    private static boolean isLabel(String label) {
        return label.codePoints().anyMatch(Character::isLetter)
                && VALUE_SHAPES.stream().noneMatch(shape -> shape.matcher(label).matches());
    }

    private static boolean hasValueColumn(List<Element> rows, int column) {
        if (rows.getFirst().child(column).text().isBlank()) {
            return false;
        }
        // ponytail: sample only the first 20 records; explicit format metadata takes precedence over inference.
        List<String> values = rows.stream().skip(1).limit(SAMPLE_ROWS)
                .filter(row -> row.childrenSize() > column).map(row -> row.child(column).text())
                .filter(value -> !value.isBlank()).toList();
        return !values.isEmpty() && VALUE_SHAPES.stream()
                .anyMatch(shape -> values.stream().allMatch(value -> shape.matcher(value).matches()));
    }
}
