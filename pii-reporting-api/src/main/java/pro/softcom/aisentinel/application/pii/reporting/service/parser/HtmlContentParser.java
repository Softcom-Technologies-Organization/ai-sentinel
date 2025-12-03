package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Parser for HTML content using sentence boundary detection (SBD) combined with block-level tags.
 * <p>
 * Uses regex-based sentence segmentation to extract context around PIIs,
 * identifying sentence boundaries by:
 * - Block-level HTML tags (p, div, h1-h6, li, tr, etc.)
 * - Punctuation followed by capital letters
 * - Newlines and other structural markers
 * <p>
 * Handles common abbreviations to avoid false sentence breaks.
 * Falls back to MAX_CONTEXT_RADIUS when no boundary is found nearby.
 * <p>
 * Uses Jsoup to clean HTML tags and convert to readable text.
 */
public class HtmlContentParser implements ContentParser {

    /**
     * Maximum number of characters to include on each side of a PII position
     * when no structural delimiters (block tags, newlines, sentence boundaries) are found nearby.
     * This prevents returning the entire document as context.
     */
    static final int MAX_CONTEXT_RADIUS = 150;

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
     * Pattern to detect sentence boundaries for finding sentence END positions.
     * Matches: punctuation (.!?) optionally followed by closing quotes/parens,
     * then whitespace, before a capital letter or end of text.
     */
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile(
        "[.!?][\"'»)\\]]?\\s+(?=[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜŸŒÆÇ])|" +  // Punctuation + capital (including French accents)
        "[.!?][\"'»)\\]]?\\s*$|" +                            // Punctuation at end of text
        "\\n\\s*(?=\\S)|" +                                   // Newline followed by non-whitespace
        "[;:]\\s+(?=[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜŸŒÆÇ])"                  // Semicolon/colon + capital letter
    );

    /**
     * Pattern to detect sentence boundaries for finding sentence START positions.
     * Looks for positions after punctuation + whitespace that precede capital letters.
     */
    private static final Pattern SENTENCE_START_PATTERN = Pattern.compile(
        "(?<=[.!?][\"'»)\\]]?)\\s+(?=[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜŸŒÆÇ])|" +  // After punctuation, before capital
        "(?<=\\n)\\s*(?=\\S)"                                       // After newline, before non-whitespace
    );

    /**
     * Common abbreviations that should NOT be treated as sentence endings.
     * Includes French, English, and common technical abbreviations.
     */
    private static final Set<String> ABBREVIATIONS = Set.of(
        // Titles
        "Dr.", "Mr.", "Mrs.", "Ms.", "Prof.", "Sr.", "Jr.", "Mme.", "Mlle.", "M.",
        // Organizations & Places
        "Inc.", "Ltd.", "Corp.", "Co.", "vs.", "U.S.", "U.K.", "E.U.",
        // Common abbreviations
        "etc.", "al.", "e.g.", "i.e.", "cf.", "Fig.", "fig.", "No.", "no.",
        "pp.", "vol.", "Vol.", "ed.", "Ed.", "Rev.", "rev.",
        // French abbreviations
        "av.", "apr.", "J.-C.", "env.", "art.", "p.", "ex."
    );

    @Override
    public int findLineStart(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        int structuralBound = findStructuralLineStart(source, safePosition);
        int characterBound = Math.max(0, safePosition - MAX_CONTEXT_RADIUS);
        return Math.max(structuralBound, characterBound);
    }

    @Override
    public int findLineEnd(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        int structuralBound = findStructuralLineEnd(source, safePosition);
        int characterBound = Math.min(source.length(), safePosition + MAX_CONTEXT_RADIUS);
        return Math.min(structuralBound, characterBound);
    }

    /**
     * Finds the structural line start before the given position.
     * Considers: block HTML tags, newlines, and sentence boundaries.
     * Uses regex-based sentence boundary detection, filtering out abbreviations.
     */
    private int findStructuralLineStart(String source, int position) {
        int lastBreak = 0;

        // Check for block-level HTML tags
        Matcher blockMatcher = BLOCK_TAGS.matcher(source);
        while (blockMatcher.find() && blockMatcher.end() <= position) {
            lastBreak = blockMatcher.end();
        }

        // Check for newline (most reliable delimiter)
        int lastNewline = source.lastIndexOf('\n', position);
        if (lastNewline >= 0 && lastNewline + 1 > lastBreak) {
            lastBreak = lastNewline + 1;
        }

        // If block tag or newline is within reasonable distance, use it
        if (lastBreak > position - MAX_CONTEXT_RADIUS) {
            return lastBreak;
        }

        // Otherwise, search for sentence boundaries
        int searchStart = Math.max(0, position - MAX_CONTEXT_RADIUS * 2);
        String searchRegion = source.substring(searchStart, position);
        
        Matcher sentenceMatcher = SENTENCE_START_PATTERN.matcher(searchRegion);
        int lastSentenceBoundary = -1;
        
        while (sentenceMatcher.find()) {
            int absolutePosition = searchStart + sentenceMatcher.start();
            // Check if this is a real sentence boundary (not an abbreviation)
            if (!isAbbreviation(source, absolutePosition)) {
                lastSentenceBoundary = searchStart + sentenceMatcher.end();
            }
        }
        
        // Return the best boundary: sentence boundary if found, otherwise structural or 0
        if (lastSentenceBoundary >= 0 && lastSentenceBoundary > lastBreak) {
            return lastSentenceBoundary;
        }
        
        return lastBreak;
    }

    /**
     * Finds the structural line end after the given position.
     * Considers: block HTML tags, newlines, and sentence boundaries.
     * Uses regex-based sentence boundary detection, filtering out abbreviations.
     */
    private int findStructuralLineEnd(String source, int position) {
        // Check for block-level HTML tags
        Matcher blockMatcher = BLOCK_TAGS.matcher(source);
        blockMatcher.region(position, source.length());
        int nextBlockTag = blockMatcher.find() ? blockMatcher.start() : source.length();

        // Check for newline
        int nextNewline = source.indexOf('\n', position);
        
        // Find the closest structural delimiter
        int structuralEnd = nextBlockTag;
        if (nextNewline >= 0 && nextNewline < structuralEnd) {
            structuralEnd = nextNewline;
        }

        // If block tag or newline is within reasonable distance, use it
        if (structuralEnd < position + MAX_CONTEXT_RADIUS) {
            return structuralEnd;
        }

        // Otherwise, search for sentence boundaries
        int searchEnd = Math.min(source.length(), position + MAX_CONTEXT_RADIUS * 2);
        String searchRegion = source.substring(position, searchEnd);
        
        Matcher sentenceMatcher = SENTENCE_END_PATTERN.matcher(searchRegion);
        
        while (sentenceMatcher.find()) {
            int absolutePosition = position + sentenceMatcher.start();
            // Check if this is a real sentence boundary (not an abbreviation)
            if (!isAbbreviation(source, absolutePosition)) {
                // Return position at end of punctuation (include the punctuation)
                return position + sentenceMatcher.end();
            }
        }
        
        return structuralEnd;
    }

    /**
     * Checks if the period at the given position is part of an abbreviation.
     * 
     * @param text The full text
     * @param dotPosition The position in text where a potential sentence-ending period was found
     * @return true if the period is part of a known abbreviation
     */
    private boolean isAbbreviation(String text, int dotPosition) {
        for (String abbr : ABBREVIATIONS) {
            int abbrLength = abbr.length();
            int startPos = dotPosition - abbrLength + 1;
            
            if (startPos >= 0 && dotPosition + 1 <= text.length()) {
                String candidate = text.substring(startPos, Math.min(dotPosition + 1, text.length()));
                // Handle cases where abbr doesn't end with period but text does
                if (candidate.equals(abbr) || 
                    (abbr.endsWith(".") && candidate.equals(abbr.substring(0, abbr.length())))) {
                    return true;
                }
            }
        }
        return false;
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
