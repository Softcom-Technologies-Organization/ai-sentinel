package pro.softcom.aisentinel.application.pii.reporting.service;

import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DiscardedSensitiveData;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.SensitiveData;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionOffsetRemapperTest {

    @Test
    void Should_RejectCrossCellFindingsAndKeepSpacedCards_When_TableHasNoUsableHeaders() {
        for (String table : new String[]{
                "<table><tr><td>1138667</td><td>2505047</td></tr><tr><td>4111 1111 1111 1111</td><td></td></tr></table>",
                "<table><tr><th colspan='2147483647'>Value</th></tr><tr><td>1138667</td><td>2505047</td></tr><tr><td>4111 1111 1111 1111</td></tr></table>",
                "<table><tr><td>1138667<table><tr><td>2505047</td><td>4111 1111 1111 1111</td></tr></table></td></tr></table>"}) {
            var content = new HtmlContentParser().extractContent(table);
            String text = content.analysisText();
            int start = text.indexOf("1138667");
            var crossing = data(text, start, text.indexOf("2505047") + 7 - start);
            var card = data(text, text.indexOf("4111"), 19);
            var detection = ContentPiiDetection.builder().sensitiveDataFound(List.of(crossing, card))
                    .discardedByPostfilter(List.of(new DiscardedSensitiveData(crossing, "FALSE_POSITIVE", 1.0, "test")))
                    .build();

            var result = DetectionOffsetRemapper.remap(detection, content);

            assertThat(result.sensitiveDataFound()).extracting(SensitiveData::value).containsExactly("4111 1111 1111 1111");
            assertThat(result.discardedByPostfilter()).isEmpty();
            var kept = result.sensitiveDataFound().getFirst();
            assertThat(content.contextText().substring(kept.startingPosition(), kept.endingPosition())).isEqualTo(kept.value());
        }
    }

    @Test
    void Should_MapEachOccurrenceAndDropSyntheticLabels_When_ContextIsEnriched() {
        ExtractedContent content = ExtractedContent.builder()
                .appendOriginal("😀 Contacts\n")
                .appendSynthetic("Email : ").appendOriginal("lea@example.org\n")
                .appendSynthetic("Email : ").appendOriginal("lea@example.org")
                .build();
        String analysis = content.analysisText();
        SensitiveData first = data(analysis, analysis.indexOf("lea@"), "lea@example.org".length());
        SensitiveData second = data(analysis, analysis.lastIndexOf("lea@"), "lea@example.org".length());
        SensitiveData label = data(analysis, analysis.indexOf("Email"), 5);
        SensitiveData crossingLabel = data(analysis, analysis.lastIndexOf("Email"), 10);
        var runStats = List.of(new DetectorRunStat(DetectorSource.MINISTRAL, 12, 4, 0, "offline"));
        ContentPiiDetection detection = ContentPiiDetection.builder()
                .sensitiveDataFound(List.of(first, label, second, crossingLabel))
                .discardedByPostfilter(List.of(new DiscardedSensitiveData(second, "FALSE_POSITIVE", 1.0, "format")))
                .detectorRunStats(runStats).statistics(Map.of("EMAIL", 4)).build();

        ContentPiiDetection result = DetectionOffsetRemapper.remap(detection, content);

        assertThat(result.sensitiveDataFound()).hasSize(2);
        assertThat(result.sensitiveDataFound()).extracting(SensitiveData::startingPosition)
                .containsExactly(content.contextText().indexOf("lea@"), content.contextText().lastIndexOf("lea@"));
        for (SensitiveData value : result.sensitiveDataFound()) {
            assertThat(content.contextText().substring(value.startingPosition(), value.endingPosition()))
                    .isEqualTo(value.value());
        }
        assertThat(result.discardedByPostfilter().getFirst().data()).isEqualTo(result.sensitiveDataFound().getLast());
        assertThat(result.discardedByPostfilter().getFirst().judgeReason()).isEqualTo("format");
        assertThat(result.detectorRunStats()).isEqualTo(runStats);
        assertThat(result.statistics()).containsExactlyEntriesOf(Map.of("EMAIL", 2));
    }

    @Test
    void Should_KeepIdentityResult_When_ContentHasNoAddedLabels() {
        ContentPiiDetection detection = ContentPiiDetection.builder().sensitiveDataFound(List.of()).build();
        assertThat(DetectionOffsetRemapper.remap(detection, ExtractedContent.identity("plain text")))
                .isSameAs(detection);
    }

    @Test
    void Should_RejectUnmappedRanges_When_ContentContainsOnlySyntheticText() {
        ExtractedContent content = ExtractedContent.builder().appendSynthetic("Email").build();
        ContentPiiDetection detection = ContentPiiDetection.builder()
                .sensitiveDataFound(List.of(data("Email", 0, 5))).build();
        assertThat(DetectionOffsetRemapper.remap(detection, content).sensitiveDataFound()).isEmpty();
    }

    private static SensitiveData data(String text, int start, int length) {
        return new SensitiveData("EMAIL", "Email", text.substring(start, start + length), "context",
                start, start + length, 0.95, "selector", DetectorSource.MINISTRAL);
    }
}
