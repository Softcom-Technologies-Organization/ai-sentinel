package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for HTML content where logical lines are delimited by block-level tags.
 * <p>
 * Block-level tags that create visual line breaks include:
 * - Paragraphs: p, div, section, article
 * - Headers: h1-h6
 * - Lists: li, ul, ol
 * - Tables: tr, td, th
 * - Line breaks: br
 * - And many others handled automatically by Jsoup
 * <p>
 * Uses Jsoup to clean HTML tags and convert to readable text.
 */
public class HtmlContentParser implements ContentParser {

    /**
     * Pattern to match HTML block-level tags that create visual line breaks.
     * Jsoup handles all standard HTML tags, but we use regex for position finding.
     */
    private static final Pattern BLOCK_TAGS = Pattern.compile("""
        (?ix)                     # i = case-insensitive, x = ignore whitespace/comments
        </?(?:
          p|div|section|article|header|footer|nav|aside|
          blockquote|pre|table|
          ul|li|ol|dl|dt|dd|
          tr|td|th|
          h\\d
        )[^>]*>                   # up to >
        | <br/?>                  # or br tag
        """,
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Per-thread cache of line boundaries for the most recent source. When a
     * batch of PII entities is enriched against the same source, we would
     * otherwise rerun the block-tag regex (and a full newline scan) for every
     * entity via {@link #findLineStart} / {@link #findLineEnd}.
     * <p>
     * The cache stores the source by <em>reference identity</em> (no hashing,
     * no retention of arbitrary strings) and is bounded to a single entry per
     * thread: whenever a different source shows up, the previous entry is
     * replaced. This makes the index lookup O(log K) per call against a
     * precomputed breakpoint table of size K, instead of O(|source|).
     */
    private final ThreadLocal<LineIndex> lineIndexCache = new ThreadLocal<>();

    @Override
    public int findLineStart(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        LineIndex index = indexFor(source);

        // Largest breakpoint <= safePosition, or 0 if none exists.
        int idx = Arrays.binarySearch(index.lineStartAfter, safePosition);
        if (idx < 0) {
            idx = -idx - 2;
        }
        return idx < 0 ? 0 : index.lineStartAfter[idx];
    }

    @Override
    public int findLineEnd(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        LineIndex index = indexFor(source);

        // Smallest breakpoint >= safePosition, or source.length() if none exists.
        int idx = Arrays.binarySearch(index.lineEndAt, safePosition);
        if (idx < 0) {
            idx = -idx - 1;
        }
        return idx >= index.lineEndAt.length ? source.length() : index.lineEndAt[idx];
    }

    private LineIndex indexFor(String source) {
        LineIndex cached = lineIndexCache.get();
        if (cached != null && cached.source == source) {
            return cached;
        }
        LineIndex fresh = computeLineIndex(source);
        lineIndexCache.set(fresh);
        return fresh;
    }

    /**
     * Scans the source once to collect every position that acts as a line
     * start (end of a block tag, or the character after a newline) and every
     * position that acts as a line end (start of a block tag, or the newline
     * character itself). The resulting arrays are sorted so that subsequent
     * lookups can use {@link Arrays#binarySearch}.
     */
    private static LineIndex computeLineIndex(String source) {
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        Matcher matcher = BLOCK_TAGS.matcher(source);
        while (matcher.find()) {
            ends.add(matcher.start());
            starts.add(matcher.end());
        }

        for (int p = source.indexOf('\n'); p >= 0; p = source.indexOf('\n', p + 1)) {
            ends.add(p);
            starts.add(p + 1);
        }

        return new LineIndex(source, toSortedArray(starts), toSortedArray(ends));
    }

    private static int[] toSortedArray(List<Integer> values) {
        int[] arr = values.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(arr);
        return arr;
    }

    @Override
    public String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            // Parse HTML with Jsoup
            Document doc = Jsoup.parse(text);

            // Configure output settings to avoid extra formatting
            doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

            // Line breaks
            doc.select("br").append("\\n");

            // Paragraphs and divisions
            doc.select("p").prepend("\\n").append("\\n");
            doc.select("div").append("\\n");
            doc.select("section").append("\\n");
            doc.select("article").append("\\n");

            // Headers
            doc.select("h1, h2, h3, h4, h5, h6").prepend("\\n").append("\\n");

            // Lists
            doc.select("ul").prepend("\\n").append("\\n");
            doc.select("ol").prepend("\\n").append("\\n");
            doc.select("li").prepend("\\n");
            doc.select("dl").prepend("\\n").append("\\n");
            doc.select("dt").prepend("\\n");
            doc.select("dd").prepend("\\n");

            // Tables
            doc.select("table").prepend("\\n").append("\\n");
            doc.select("tr").append("\\n");
            doc.select("td").append(" "); // Space between cells
            doc.select("th").append(" ");

            // Semantic elements
            doc.select("header").append("\\n");
            doc.select("footer").append("\\n");
            doc.select("nav").append("\\n");
            doc.select("aside").append("\\n");

            // Block quotes and pre
            doc.select("blockquote").prepend("\\n").append("\\n");
            doc.select("pre").prepend("\\n").append("\\n");

            // Extract text content
            String cleaned = doc.text();

            // Convert escaped newlines back to actual newlines
            cleaned = cleaned.replace("\\n", "\n");

            // Normalize whitespace: collapse multiple newlines/spaces into single newline
            // This prevents patterns like "\n \n \n" that confuse PII detection models
            cleaned = cleaned.replaceAll("[ \\t]*+\\n[ \\t]*+", "\n");  // Remove spaces around newlines (possessive quantifiers prevent ReDoS)
            cleaned = cleaned.replaceAll("\\n{2,}", "\n\n");            // Max 2 consecutive newlines
            cleaned = cleaned.replaceAll("[ \\t]{2,}+", " ");           // Collapse multiple spaces/tabs (possessive quantifier)

            return cleaned.trim();
        } catch (Exception _) {
            // Fallback: return original text if Jsoup parsing fails
            return text;
        }
    }

    @Override
    public ContentType getContentType() {
        return ContentType.HTML;
    }

    /**
     * Precomputed line boundary table for a given source.
     * Retained by reference identity to enable fast cache hit/miss checks.
     */
    private static final class LineIndex {
        private final String source;
        private final int[] lineStartAfter;
        private final int[] lineEndAt;

        LineIndex(String source, int[] lineStartAfter, int[] lineEndAt) {
            this.source = source;
            this.lineStartAfter = lineStartAfter;
            this.lineEndAt = lineEndAt;
        }
    }
}
