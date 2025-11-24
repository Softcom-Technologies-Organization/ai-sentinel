package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

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

    @Override
    public int findLineStart(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());

        // Find the last block tag or newline before the position
        int lastBreak = 0;

        // Check for block tags
        Matcher matcher = BLOCK_TAGS.matcher(source);
        while (matcher.find() && matcher.end() <= safePosition) {
            lastBreak = matcher.end();
        }

        // Also consider newline characters
        int lastNewline = source.lastIndexOf('\n', safePosition);
        if (lastNewline >= 0 && lastNewline + 1 > lastBreak) {
            lastBreak = lastNewline + 1;
        }

        return lastBreak;
    }

    @Override
    public int findLineEnd(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());

        // Find the next block tag or newline after the position
        Matcher matcher = BLOCK_TAGS.matcher(source);
        matcher.region(safePosition, source.length());

        int nextBlockTag = matcher.find() ? matcher.start() : source.length();
        int nextNewline = source.indexOf('\n', safePosition);

        // Return the closest boundary
        if (nextNewline >= 0 && nextNewline < nextBlockTag) {
            return nextNewline;
        }

        return nextBlockTag;
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
            return cleaned.replace("\\n", "\n");
        } catch (Exception _) {
            // Fallback: return original text if Jsoup parsing fails
            return text;
        }
    }

    @Override
    public ContentType getContentType() {
        return ContentType.HTML;
    }
}
