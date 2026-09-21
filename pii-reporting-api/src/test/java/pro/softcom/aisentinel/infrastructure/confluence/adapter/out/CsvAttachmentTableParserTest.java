package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvAttachmentTableParserTest {

    @Test
    void Should_PreserveCells_When_QuotedSeparatorsAndLineBreaks() {
        Document table = CsvAttachmentTableParser.parse("Nom;Email\n\"Durand;Jean\";\"jean@example.test\"\n\"Martin\nAlice\";alice@example.test").orElseThrow();

        assertThat(table.select("tr")).hasSize(3);
        assertThat(table.select("tr").get(1).children().eachText()).containsExactly("Durand;Jean", "jean@example.test");
        assertThat(table.select("tr").get(2).child(0).wholeText()).isEqualTo("Martin\nAlice");
    }

    @Test
    void Should_PreserveEmptyColumns_When_CommaDelimited() {
        Document table = CsvAttachmentTableParser.parse("Nom,Email,Port\nAlice,,587").orElseThrow();

        assertThat(table.select("tr").get(1).children()).hasSize(3);
        assertThat(table.select("tr").get(1).child(2).text()).isEqualTo("587");
    }

    @Test
    void Should_RequestPlainTextFallback_When_NoReliableDelimiter() {
        for (String input : new String[]{"", "ordinary text", "Nom;Email\nAlice;example@test;extra", "Nom;Email\n\"unfinished"}) {
            assertThat(CsvAttachmentTableParser.parse(input)).isEmpty();
        }
        assertThat(CsvAttachmentTableParser.parse(null)).isEmpty();
    }

    @Test
    void Should_RequestPlainTextFallback_When_DelimiterIsAmbiguous() {
        assertThat(CsvAttachmentTableParser.parse("A,B;C\nD,E;F")).isEmpty();
    }
}
