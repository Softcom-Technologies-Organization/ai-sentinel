package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for plain text content using sentence boundary detection (SBD).
 * <p>
 * Uses regex-based sentence segmentation to extract context around PIIs,
 * identifying sentence boundaries by punctuation followed by capital letters,
 * newlines, and other structural markers.
 * <p>
 * Handles common abbreviations to avoid false sentence breaks.
 * Falls back to MAX_CONTEXT_RADIUS when no sentence boundary is found nearby.
 */
public class PlainTextParser implements ContentParser {

    /**
     * Maximum radius (in characters) from the PII position to include in context.
     * Used as ultimate fallback when no sentence boundary is found nearby.
     * Total context will be at most 2 * MAX_CONTEXT_RADIUS + PII length.
     */
    static final int MAX_CONTEXT_RADIUS = 150;

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
        int sentenceBound = findSentenceStart(source, safePosition);
        int characterBound = Math.max(0, safePosition - MAX_CONTEXT_RADIUS);
        
        // Return the closest (maximum) bound to avoid excessive context
        return Math.max(sentenceBound, characterBound);
    }

    @Override
    public int findLineEnd(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        int sentenceBound = findSentenceEnd(source, safePosition);
        int characterBound = Math.min(source.length(), safePosition + MAX_CONTEXT_RADIUS);
        
        // Return the closest (minimum) bound to avoid excessive context
        return Math.min(sentenceBound, characterBound);
    }

    /**
     * Finds the sentence start position before the given position.
     * Uses regex-based sentence boundary detection, filtering out abbreviations.
     */
    private int findSentenceStart(String source, int position) {
        // First check for simple newline (most reliable delimiter)
        int lastNewline = source.lastIndexOf('\n', position);
        if (lastNewline >= 0 && lastNewline > position - MAX_CONTEXT_RADIUS) {
            return lastNewline + 1;
        }

        // Search for sentence boundaries in the region before position
        int searchStart = Math.max(0, position - MAX_CONTEXT_RADIUS * 2);
        String searchRegion = source.substring(searchStart, position);
        
        Matcher matcher = SENTENCE_START_PATTERN.matcher(searchRegion);
        int lastBoundary = -1;
        
        while (matcher.find()) {
            int absolutePosition = searchStart + matcher.start();
            // Check if this is a real sentence boundary (not an abbreviation)
            if (!isAbbreviation(source, absolutePosition)) {
                lastBoundary = searchStart + matcher.end();
            }
        }
        
        return lastBoundary >= 0 ? lastBoundary : 0;
    }

    /**
     * Finds the sentence end position after the given position.
     * Uses regex-based sentence boundary detection, filtering out abbreviations.
     */
    private int findSentenceEnd(String source, int position) {
        // First check for simple newline (most reliable delimiter)
        int nextNewline = source.indexOf('\n', position);
        if (nextNewline >= 0 && nextNewline < position + MAX_CONTEXT_RADIUS) {
            return nextNewline;
        }

        // Search for sentence boundaries in the region after position
        int searchEnd = Math.min(source.length(), position + MAX_CONTEXT_RADIUS * 2);
        String searchRegion = source.substring(position, searchEnd);
        
        Matcher matcher = SENTENCE_END_PATTERN.matcher(searchRegion);
        
        while (matcher.find()) {
            int absolutePosition = position + matcher.start();
            // Check if this is a real sentence boundary (not an abbreviation)
            if (!isAbbreviation(source, absolutePosition)) {
                // Return position at end of punctuation (include the punctuation)
                return position + matcher.end();
            }
        }
        
        return source.length();
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
        // Plain text doesn't need cleaning
        return text;
    }

    @Override
    public ContentType getContentType() {
        return ContentType.PLAIN_TEXT;
    }
}
