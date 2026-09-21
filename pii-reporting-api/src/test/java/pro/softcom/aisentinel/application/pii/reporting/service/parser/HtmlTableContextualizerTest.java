package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTableContextualizerTest {

    private final HtmlContentParser parser = new HtmlContentParser();

    @Test
    void Should_CombineColumnLevelsAndRowHeaders_When_CellsAreMerged() {
        var content = parser.extractContent("""
            <table><thead><tr><th rowspan="2">Personne</th><th colspan="2">Contact</th></tr>
            <tr><th>Courriel</th><th>Téléphone</th></tr></thead><tbody>
            <tr><th scope="row" rowspan="2">Alice</th><td>a@example.ch</td><td>021 123 45 67</td></tr>
            <tr><td>b@example.ch</td><td>022 123 45 67</td></tr></tbody></table>
            """);

        assertThat(content.analysisText()).contains("Personne : Alice",
            "Contact / Courriel / Alice : a@example.ch", "Contact / Courriel / Alice : b@example.ch",
            "Contact / Téléphone / Alice : 022 123 45 67");
        assertThat(content.contextText().split("Alice", -1)).hasSize(2);
    }

    @Test
    void Should_UseExplicitHeaderIds_When_TheirOrderDiffersFromColumns() {
        var content = parser.extractContent("""
            <table><tr><th id="port">Port</th><th id="pin">Code PIN</th></tr>
            <tr><td headers="pin">1234</td><td headers="port">587</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Code PIN : 1234 | Port : 587");
    }

    @Test
    void Should_NotInventHeader_When_ExplicitReferenceIsMissing() {
        var content = parser.extractContent("""
            <table><tr><th>Port</th></tr><tr><td headers="missing">1234</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("1234").doesNotContain("Port : 1234");
    }

    @Test
    void Should_RespectRowGroups_When_HeaderSpansRemainingRows() {
        var content = parser.extractContent("""
            <table><tbody><tr><th scope="rowgroup" rowspan="0">Personnel</th><td>Alice</td></tr>
            <tr><td>Bob</td></tr></tbody><tbody><tr><td>Externes</td><td>Carol</td></tr></tbody></table>
            """);

        assertThat(content.analysisText()).contains("Personnel : Alice", "Personnel : Bob", "Carol")
            .doesNotContain("Personnel : Carol");
    }

    @Test
    void Should_KeepAllTextWithoutAssociations_When_SpansAreHostileOrTablesAreNested() {
        for (String html : new String[]{
            "<table><tr><th colspan='2147483647'>Code</th></tr><tr><td>1234</td></tr></table>",
            "<table><tr><th>Code</th></tr><tr><td>1234<table><tr><th>Inner</th><td>5678</td></tr></table></td></tr></table>"
        }) {
            var content = parser.extractContent(html);

            assertThat(content.analysisText().replace(" | ", " ").strip()).isEqualTo(parser.cleanText(html));
            assertThat(content.contextText()).isEqualTo(content.analysisText());
        }
    }

    @Test
    void Should_PreserveBlankAndExtraCells_When_HeaderWidthsDiffer() {
        var content = parser.extractContent("""
            <table><thead><tr><th>Nom</th><th></th></tr></thead>
            <tr><td>Alice</td><td>1234</td><td>extra@example.ch</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Nom : Alice", "1234", "extra@example.ch")
            .doesNotContain("Colonne", " : 1234", " : extra@example.ch");
    }

    @Test
    void Should_KeepCellOnOneLine_When_CellContainsBlocksAndNewlines() {
        var content = parser.extractContent("""
            <table><tr><th>Adresse</th></tr><tr><td><p>Rue du Lac 4</p><p>1000 Lausanne<br>Suisse</p></td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Adresse : Rue du Lac 4 1000 Lausanne Suisse");
    }

    @Test
    void Should_NotMixHeaderIds_When_TwoTablesReuseThem() {
        var content = parser.extractContent("""
            <table><tr><th id="value">Port</th></tr><tr><td headers="value">587</td></tr></table>
            <table><tr><th id="value">Code PIN</th></tr><tr><td headers="value">1234</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Port : 587", "Code PIN : 1234")
            .doesNotContain("Port : 1234", "Code PIN : 587");
    }

    @Test
    void Should_UseCurrentHeaderBlock_When_A_TableContainsSuccessiveSections() {
        var content = parser.extractContent("""
            <table><tr><th>Port</th></tr><tr><td>587</td></tr>
            <tr><th>Code PIN</th></tr><tr><td>1234</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Port : 587", "Code PIN : 1234")
            .doesNotContain("Port / Code PIN", "Port : Code PIN");
    }

    @Test
    void Should_RespectGroupsAndCase_When_ResolvingHeaderScope() {
        var content = parser.extractContent("""
            <table><tbody><tr><th scope=" ROW ">Port</th><td>587</td></tr></tbody>
            <tbody><tr><th>Code PIN</th></tr><tr><td>1234</td></tr></tbody></table>
            """);

        assertThat(content.analysisText()).contains("Port : 587", "Code PIN : 1234")
            .doesNotContain("Port / Code PIN", "Port : 1234");
    }

    @Test
    void Should_RestoreOriginalText_When_AssociationBudgetIsExceededAfterPartialExtraction() {
        String html = "<table>" + "<tr><th scope='rowgroup'>Groupe</th><td>1234</td></tr>".repeat(350) + "</table>";

        var content = parser.extractContent(html);

        assertThat(content.analysisText().replace(" | ", " ")).isEqualTo(parser.cleanText(html));
        assertThat(content.contextText()).isEqualTo(content.analysisText());
        assertThat(content.analysisText()).doesNotContain("AISENTINEL");
    }

    @Test
    void Should_UseDeclaredColumnGroup_When_HeaderHasColgroupScope() {
        var content = parser.extractContent("""
            <table><colgroup span="2"></colgroup><colgroup></colgroup>
            <tr><th scope="colgroup">Contact</th><th>Courriel</th><th>Port</th></tr>
            <tr><td>Alice</td><td>a@example.ch</td><td>587</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Contact : Alice", "Contact / Courriel : a@example.ch", "Port : 587")
            .doesNotContain("Contact / Port");
    }

    @Test
    void Should_RestoreOriginalText_When_RepeatedHeaderWouldAmplifyContentExcessively() {
        String html = "<table><tr><th>" + "Header".repeat(10_000) + "</th></tr>"
            + "<tr><td>1234</td></tr>".repeat(40) + "</table>";

        var content = parser.extractContent(html);

        assertThat(content.analysisText()).isEqualTo(parser.cleanText(html));
        assertThat(content.contextText()).isEqualTo(content.analysisText());
    }

    @Test
    void Should_FallBackWithoutGuessingColumns_When_MergedCellsOverlap() {
        String html = """
            <table><tr><th>A</th><th rowspan="2">B</th><th>C</th></tr>
            <tr><td colspan="2">1234</td></tr></table>
            """;

        assertThat(parser.extractContent(html).analysisText().replace(" | ", " ")).isEqualTo(parser.cleanText(html));
    }

    @Test
    void Should_NotReuseMissingHeaders_When_NextSectionHasFewerColumns() {
        var content = parser.extractContent("""
            <table><tr><th>Port</th><th>Mot de passe</th></tr><tr><td>587</td><td>secret</td></tr>
            <tr><th>Service</th></tr><tr><td>SMTP</td><td>587</td></tr></table>
            """);

        assertThat(content.analysisText()).contains("Service : SMTP | 587")
            .doesNotContain("Mot de passe : 587");
    }

    @Test
    void Should_ShareExpansionBudget_When_PageContainsManyTables() {
        String table = "<table><tr><th>" + "Header".repeat(1_000) + "</th></tr>"
            + "<tr><td>1234</td></tr>".repeat(150) + "</table>";

        var content = parser.extractContent(table.repeat(3));

        assertThat(content.analysisText().length()).isLessThan(2_100_000);
        assertThat(content.analysisText().split(" : 1234", -1)).hasSize(301);
        assertThat(content.contextText().split("1234", -1)).hasSize(451);
    }
}
