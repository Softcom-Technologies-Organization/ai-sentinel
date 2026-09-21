package pro.softcom.aisentinel.domain.confluence.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ExtractedContent(String analysisText, String contextText, List<Segment> segments) {

    public ExtractedContent {
        Objects.requireNonNull(analysisText, "analysisText");
        Objects.requireNonNull(contextText, "contextText");
        segments = List.copyOf(segments);
        int analysisEnd = 0;
        int contextEnd = 0;
        for (Segment segment : segments) {
            if (segment.analysisStart() < analysisEnd || segment.contextStart() != contextEnd
                || segment.length() > analysisText.length() - segment.analysisStart()
                || segment.length() > contextText.length() - segment.contextStart()
                || !analysisText.regionMatches(segment.analysisStart(), contextText,
                    segment.contextStart(), segment.length())) {
                throw new IllegalArgumentException("Segments must map identical, ordered text and cover the context");
            }
            analysisEnd = segment.analysisStart() + segment.length();
            contextEnd = segment.contextStart() + segment.length();
        }
        if (contextEnd != contextText.length()) {
            throw new IllegalArgumentException("Segments must cover the entire context text");
        }
    }

    public static ExtractedContent identity(String text) {
        return new ExtractedContent(text, text, text.isEmpty() ? List.of() : List.of(new Segment(0, 0, text.length())));
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Segment(int analysisStart, int contextStart, int length) {
        public Segment {
            if (analysisStart < 0 || contextStart < 0 || length <= 0) {
                throw new IllegalArgumentException("Segment positions must be nonnegative and length must be positive");
            }
        }
    }

    public static final class Builder {
        private final StringBuilder analysis = new StringBuilder();
        private final StringBuilder context = new StringBuilder();
        private final List<Segment> segments = new ArrayList<>();

        public Builder appendOriginal(String text) {
            if (!text.isEmpty()) {
                segments.add(new Segment(analysis.length(), context.length(), text.length()));
                analysis.append(text);
                context.append(text);
            }
            return this;
        }

        public Builder appendSynthetic(String text) {
            analysis.append(Objects.requireNonNull(text, "text"));
            return this;
        }

        public Builder append(ExtractedContent content) {
            for (Segment segment : content.segments()) {
                segments.add(new Segment(analysis.length() + segment.analysisStart(),
                    context.length() + segment.contextStart(), segment.length()));
            }
            analysis.append(content.analysisText());
            context.append(content.contextText());
            return this;
        }

        public ExtractedContent build() {
            return new ExtractedContent(analysis.toString(), context.toString(), segments);
        }

    }
}
