package pro.softcom.aisentinel.domain.confluence.extraction;

import org.junit.jupiter.api.Test;
import pro.softcom.aisentinel.domain.confluence.extraction.ExtractedContent.Segment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ExtractedContentTest {

    @Test
    void Should_MapEntireText_When_Identity() {
        assertThat(ExtractedContent.identity("Élodie 😀").segments())
            .containsExactly(new Segment(0, 0, 9));
        assertThat(ExtractedContent.identity("").segments()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> ExtractedContent.identity(null));
    }

    @Test
    void Should_PreserveSegmentBoundaries_When_AppendingContextualContent() {
        var cell = ExtractedContent.builder().appendSynthetic("Nom : ").appendOriginal("Élodie").build();
        var content = ExtractedContent.builder().appendOriginal("Avant ").appendOriginal("😀 ")
            .append(cell).appendOriginal(" après").build();

        assertThat(content.analysisText()).isEqualTo("Avant 😀 Nom : Élodie après");
        assertThat(content.contextText()).isEqualTo("Avant 😀 Élodie après");
        assertThat(content.segments()).containsExactly(new Segment(0, 0, 6), new Segment(6, 6, 3),
                new Segment(15, 9, 6), new Segment(21, 15, 6));
    }

    @Test
    void Should_KeepNoMapping_When_ContentIsEntirelySynthetic() {
        var content = ExtractedContent.builder().appendSynthetic("Nom : ").build();

        assertThat(content.contextText()).isEmpty();
        assertThat(content.segments()).isEmpty();
    }

    @Test
    void Should_CopySegments_When_ConstructedFromMutableList() {
        var segments = new ArrayList<>(List.of(new Segment(0, 0, 1)));
        var content = new ExtractedContent("a", "a", segments);
        segments.clear();

        assertThat(content.segments()).containsExactly(new Segment(0, 0, 1));
    }

    @Test
    void Should_RejectInvalidMapping_When_CharactersOrBoundsDiffer() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Segment(-1, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Segment(0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ExtractedContent("a", "b", List.of(new Segment(0, 0, 1))));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ExtractedContent("a", "a", List.of(new Segment(Integer.MAX_VALUE, 0, 1))));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ExtractedContent("a", "a", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ExtractedContent("ab", "ab", List.of(new Segment(1, 1, 1), new Segment(0, 0, 1))));
    }
}
