package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

final class HtmlTableContextualizer {

    private static final String TABLE_TAG = "table";
    private static final String THEAD_TAG = "thead";
    private static final String COLGROUP_SCOPE = "colgroup";
    private static final int MAX_COLUMNS = 1_000;
    private static final int MAX_SPAN = 1_000;
    private static final int MAX_CELLS = 100_000;
    private static final int MAX_ANALYSIS_CHARACTERS = 2_000_000;

    private HtmlTableContextualizer() {
    }

    static ExtractedContent extract(Document document, HtmlContentParser parser) {
        List<Replacement> replacements = new ArrayList<>();
        long remainingCharacters = MAX_ANALYSIS_CHARACTERS;
        for (Element table : document.select(TABLE_TAG)) {
            if (table.parents().stream().anyMatch(parent -> parent.normalName().equals(TABLE_TAG))) {
                continue;
            }
            ExtractedContent content = extractTable(table.clone(), parser, remainingCharacters);
            remainingCharacters -= content.analysisText().length();
            String marker = marker();
            replacements.add(new Replacement(marker, content));
            table.replaceWith(new Element("p").text(marker));
        }
        return restore(parser.cleanText(document.outerHtml()), replacements);
    }

    private static ExtractedContent extractTable(Element table, HtmlContentParser parser, long remainingCharacters) {
        Element original = table.clone();
        if (table.select(TABLE_TAG).size() > 1 || table.select("th, thead td").isEmpty()) {
            return isolateCells(original, parser);
        }
        try {
            List<Cell> cells = placeCells(table);
            HeaderIndex headers = new HeaderIndex(cells, columnGroups(table));
            List<Replacement> replacements = new ArrayList<>();
            long characters = 0;
            for (Cell cell : cells) {
                ExtractedContent content = contextualize(cell, headers, remainingCharacters - characters);
                characters += content.analysisText().length();
                String marker = marker();
                replacements.add(new Replacement(marker, content));
                cell.element().text(marker);
                if (cell.element().nextElementSibling() != null) {
                    cell.element().appendText(" | ");
                }
            }
            ExtractedContent content = restore(parser.cleanText(table.outerHtml()), replacements);
            return content.analysisText().length() <= remainingCharacters
                ? content : isolateCells(original, parser);
        } catch (IllegalArgumentException _) {
            return isolateCells(original, parser);
        }
    }

    static ExtractedContent isolateCells(Element table, HtmlContentParser parser) {
        String boundary = marker();
        for (Element cell : table.select("td, th")) {
            cell.prependText(boundary).appendText(boundary);
            if (cell.nextElementSibling() != null) {
                cell.appendText(" | ");
            }
        }
        var builder = ExtractedContent.builder();
        for (String part : parser.cleanText(table.outerHtml()).split(boundary, -1)) {
            builder.appendOriginal(part);
        }
        return builder.build();
    }

    private static ExtractedContent contextualize(Cell cell, HeaderIndex headers, long remainingCharacters) {
        var builder = ExtractedContent.builder();
        List<String> labels = headers.forCell(cell).stream().map(Cell::text).filter(text -> !text.isBlank()).toList();
        long labelLength = labels.stream().mapToLong(String::length).sum() + labels.size() * 3L;
        if (cell.text().length() + labelLength > remainingCharacters) {
            throw new IllegalArgumentException("Table exceeds the contextualization text limit");
        }
        if (!cell.text().isBlank() && !labels.isEmpty()) {
            builder.appendSynthetic(String.join(" / ", labels) + " : ");
        }
        return builder.appendOriginal(cell.text()).build();
    }

    private static ExtractedContent restore(String text, List<Replacement> replacements) {
        var builder = ExtractedContent.builder();
        int cursor = 0;
        for (Replacement replacement : replacements) {
            int start = text.indexOf(replacement.marker(), cursor);
            if (start < 0) {
                throw new IllegalArgumentException("The HTML parser did not preserve the cell marker");
            }
            builder.appendOriginal(text.substring(cursor, start)).append(replacement.content());
            cursor = start + replacement.marker().length();
        }
        return builder.appendOriginal(text.substring(cursor)).build();
    }

    private static List<Cell> placeCells(Element table) {
        List<Element> rows = table.select("tr");
        Map<Element, Integer> groupEnds = new HashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            groupEnds.put(rows.get(row).parent(), row + 1);
        }
        List<Cell> cells = new ArrayList<>();
        List<Cell> occupied = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            int currentRow = row;
            occupied.removeIf(cell -> cell.rowEnd() <= currentRow);
            List<Cell> placed = placeRow(rows.get(row), row, groupEnds, occupied);
            cells.addAll(placed);
            if (cells.size() > MAX_CELLS) {
                throw new IllegalArgumentException("Table exceeds the contextualization cell limit");
            }
            occupied.addAll(placed);
            occupied.sort(Comparator.comparingInt(Cell::column));
        }
        return cells;
    }

    private static List<Cell> placeRow(Element row, int position, Map<Element, Integer> groupEnds, List<Cell> occupied) {
        List<Element> elements = row.children().stream()
            .filter(element -> element.normalName().equals("td") || element.normalName().equals("th")).toList();
        boolean headerRow = row.parent().normalName().equals(THEAD_TAG)
            || elements.stream().allMatch(element -> element.normalName().equals("th"));
        List<Cell> cells = new ArrayList<>();
        int column = 0;
        for (Element element : elements) {
            int width = span(element, "colspan");
            column = nextColumn(column, width, occupied);
            int height = span(element, "rowspan");
            int groupEnd = groupEnds.get(row.parent());
            int rowEnd = height == 0 ? groupEnd : Math.min(position + height, groupEnd);
            cells.add(new Cell(element, element.text(), position, rowEnd, column, column + width,
                row.parent(), headerRow));
            column += width;
        }
        return cells;
    }

    private static int nextColumn(int column, int width, List<Cell> occupied) {
        for (Cell cell : occupied) {
            if (column >= cell.column() && column < cell.columnEnd()) {
                column = cell.columnEnd();
            } else if (column < cell.column() && column + width > cell.column()) {
                throw new IllegalArgumentException("Merged table cells overlap");
            }
        }
        if (column + width > MAX_COLUMNS) {
            throw new IllegalArgumentException("Table exceeds the contextualization column limit");
        }
        return column;
    }

    private static int span(Element cell, String attribute) {
        String raw = cell.attr(attribute).trim();
        if (raw.isEmpty()) {
            return 1;
        }
        int value = Integer.parseInt(raw);
        if (value < 0 || value > MAX_SPAN || (value == 0 && !attribute.equals("rowspan"))) {
            throw new IllegalArgumentException("Invalid or excessive table span");
        }
        return value;
    }

    private static String marker() {
        return "AISENTINEL" + UUID.randomUUID().toString().replace("-", "") + "END";
    }

    private static List<ColumnGroup> columnGroups(Element table) {
        List<ColumnGroup> groups = new ArrayList<>();
        int column = 0;
        for (Element group : table.children().select(COLGROUP_SCOPE)) {
            long width = group.children().isEmpty() ? span(group, "span")
                : group.children().stream().mapToLong(child -> span(child, "span")).sum();
            if (width > MAX_COLUMNS - column) {
                throw new IllegalArgumentException("Table exceeds the column group limit");
            }
            groups.add(new ColumnGroup(column, column + (int) width));
            column += width;
        }
        return groups;
    }

    private record ColumnGroup(int start, int end) {
    }

    private record Replacement(String marker, ExtractedContent content) {
    }

    private record Cell(Element element, String text, int row, int rowEnd, int column, int columnEnd,
                        Element group, boolean headerRow) {
        boolean isHeader() {
            return element.normalName().equals("th") || group.normalName().equals(THEAD_TAG);
        }

        boolean isColumnHeader() {
            String scope = scope();
            return scope.equals("col") || scope.equals(COLGROUP_SCOPE)
                || (scope.isEmpty() && headerRow);
        }

        String scope() {
            String scope = element.attr("scope").trim().toLowerCase(Locale.ROOT);
            return switch (scope) {
                case "col", COLGROUP_SCOPE, "row", "rowgroup" -> scope;
                default -> "";
            };
        }
    }

    private static final class HeaderIndex {
        private final Map<String, Cell> byId = new HashMap<>();
        private final Map<Integer, List<Cell>> byColumn = new HashMap<>();
        private final Map<Integer, List<Cell>> byRow = new HashMap<>();
        private final Map<Element, List<Cell>> byRowGroup = new HashMap<>();
        private final Map<Cell, Integer> blocks = new HashMap<>();
        private final Map<Element, TreeMap<Integer, Integer>> blocksByRow = new HashMap<>();
        private final List<ColumnGroup> columnGroups;
        private int visitedHeaders;

        private HeaderIndex(List<Cell> cells, List<ColumnGroup> columnGroups) {
            this.columnGroups = columnGroups;
            int indexed = 0;
            int lastHeaderRow = -1;
            int block = 0;
            for (Cell cell : cells) {
                if (cell.isHeader()) {
                    if (cell.isColumnHeader()) {
                        if (cell.row() > lastHeaderRow + 1) {
                            block++;
                        }
                        blocks.put(cell, block);
                        blocksByRow.computeIfAbsent(cell.group(), _ -> new TreeMap<>()).put(cell.row(), block);
                        lastHeaderRow = cell.row();
                    }
                    indexed += index(cell);
                }
                if (indexed > MAX_CELLS) {
                    throw new IllegalArgumentException("Table exceeds the header association limit");
                }
            }
        }

        private int index(Cell cell) {
            if (!cell.element().id().isEmpty() && byId.putIfAbsent(cell.element().id(), cell) != null) {
                throw new IllegalArgumentException("Table header IDs must be unique");
            }
            if (cell.scope().equals("rowgroup")) {
                byRowGroup.computeIfAbsent(cell.group(), _ -> new ArrayList<>()).add(cell);
                return 1;
            }
            Map<Integer, List<Cell>> target = cell.isColumnHeader() ? byColumn : byRow;
            int start = cell.isColumnHeader() ? cell.column() : cell.row();
            int end = cell.isColumnHeader() ? cell.columnEnd() : cell.rowEnd();
            if (cell.scope().equals(COLGROUP_SCOPE)) {
                ColumnGroup group = columnGroups.stream()
                    .filter(candidate -> cell.column() >= candidate.start() && cell.column() < candidate.end())
                    .findFirst().orElse(new ColumnGroup(start, end));
                start = group.start();
                end = group.end();
            }
            for (int position = start; position < end; position++) {
                target.computeIfAbsent(position, _ -> new ArrayList<>()).add(cell);
            }
            return end - start;
        }

        private List<Cell> forCell(Cell cell) {
            if (cell.element().hasAttr("headers")) {
                return explicitHeaders(cell);
            }
            var result = new LinkedHashSet<Cell>();
            result.addAll(columnHeaders(cell));
            candidates(byRow, cell.row()).stream()
                .filter(header -> header.columnEnd() <= cell.column()
                    || header.scope().equals("row")).forEach(result::add);
            result.addAll(candidates(byRowGroup, cell.group()));
            result.remove(cell);
            return result.stream().sorted(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::column)).toList();
        }

        private List<Cell> columnHeaders(Cell cell) {
            var candidates = new LinkedHashSet<Cell>();
            for (int column = cell.column(); column < cell.columnEnd(); column++) {
                candidates(byColumn, column).stream().filter(header -> header.rowEnd() <= cell.row())
                    .filter(header -> header.group() == cell.group() || header.group().normalName().equals(THEAD_TAG))
                    .forEach(candidates::add);
            }
            return candidates.stream().filter(header -> blocks.get(header)
                .equals(blocksByRow.get(header.group()).floorEntry(cell.row()).getValue())).toList();
        }

        private <K> List<Cell> candidates(Map<K, List<Cell>> index, K key) {
            List<Cell> result = index.getOrDefault(key, List.of());
            visitedHeaders += result.size();
            if (visitedHeaders > MAX_CELLS) {
                throw new IllegalArgumentException("Table exceeds the header association work limit");
            }
            return result;
        }

        private List<Cell> explicitHeaders(Cell cell) {
            var result = new LinkedHashSet<Cell>();
            for (String id : cell.element().attr("headers").trim().split("\\s+")) {
                Cell header = byId.get(id);
                if (header == null) {
                    return List.of();
                }
                if (header != cell) {
                    result.add(header);
                }
            }
            return List.copyOf(result);
        }
    }
}
