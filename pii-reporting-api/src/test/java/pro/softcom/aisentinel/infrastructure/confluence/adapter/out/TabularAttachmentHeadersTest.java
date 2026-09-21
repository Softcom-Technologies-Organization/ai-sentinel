package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TabularAttachmentHeadersTest {

    @Test
    void Should_MarkHeaderAndPreserveEveryCell_When_ColumnValuesHaveDifferentTypes() {
        Document document = Jsoup.parse("""
                <table><tr><td>Nom</td><td>E-mail</td><td>Port</td></tr>
                <tr><td>Durand</td><td></td><td>587</td></tr></table>
                """);

        TabularAttachmentHeaders.mark(document);

        assertThat(document.select("th").eachText()).containsExactly("Nom", "E-mail", "Port");
        assertThat(document.select("tr").get(1).children()).hasSize(3);
        assertThat(document.select("td").eachText()).containsExactly("Durand", "587");
    }

    @Test
    void Should_UseVerbatimHeaders_When_LabelsAreUnknownDuplicatedOrBlank() {
        Document document = Jsoup.parse("""
                <table><tr><td>Référence métier №2</td><td>北座標</td><td>北座標</td><td></td></tr>
                <tr><td>Signy-Avenex</td><td>1138667</td><td>2505047</td><td>Ouest</td></tr>
                <tr><td>Morges</td><td>1151849</td><td>2527443</td><td>Centre</td></tr></table>
                """);

        TabularAttachmentHeaders.mark(document);

        assertThat(document.select("th")).hasSize(4);
        assertThat(document.select("th").getFirst().text()).isEqualTo("Référence métier №2");
        assertThat(document.select("th").get(1).text()).isEqualTo("北座標");
    }

    @Test
    void Should_KeepFirstRowAsData_When_TypesAreConsistentOrEvidenceConflicts() {
        for (String rows : new String[]{
                "<tr><td>Alice</td><td>1138667</td></tr><tr><td>Bob</td><td>1151849</td></tr>",
                "<tr><td>Arbitrary title</td><td>Other title</td></tr><tr><td>Alice</td><td>Bob</td></tr>",
                "<tr><td>Reference</td><td>Value</td></tr><tr><td>A</td><td>123</td></tr><tr><td>B</td><td>text</td></tr>"}) {
            Document document = Jsoup.parse("<table>" + rows + "</table>");
            TabularAttachmentHeaders.mark(document);
            assertThat(document.select("th")).isEmpty();
        }
    }

    @Test
    void Should_LeaveRowsUnchanged_When_NoReliableHeader() {
        for (String row : new String[]{"<td>Alice</td><td>Martin</td>",
                "<td>Nom</td><td>12345</td>", "<td>Email</td><td>Email</td>",
                "<td colspan='2'>Identité</td><td>Port</td>", "<td></td><td>Port</td>",
                "<td>Service</td><td>Unexpected</td>"}) {
            Document document = Jsoup.parse("<table><tr>" + row
                    + "</tr><tr><td>Paul</td><td>Smith</td></tr></table>");
            String original = document.outerHtml();

            TabularAttachmentHeaders.mark(document);

            assertThat(document.outerHtml()).isEqualTo(original);
        }
    }

    @Test
    void Should_PreserveExplicitHeaders_When_TableAlreadyDefinesThem() {
        Document document = Jsoup.parse("""
                <table><thead><tr><th>Unusual heading</th></tr></thead>
                <tbody><tr><td>Email</td></tr><tr><td>value</td></tr></tbody></table>
                """);
        String original = document.outerHtml();

        TabularAttachmentHeaders.mark(document);

        assertThat(document.outerHtml()).isEqualTo(original);
    }

    @Test
    void Should_LeaveEmptyOrHeaderOnlyContentUnchanged_When_NoRecords() {
        TabularAttachmentHeaders.mark(null);
        Document document = Jsoup.parse("<table><tr><td>Email</td></tr></table>");
        String original = document.outerHtml();

        TabularAttachmentHeaders.mark(document);

        assertThat(document.outerHtml()).isEqualTo(original);
    }
}
