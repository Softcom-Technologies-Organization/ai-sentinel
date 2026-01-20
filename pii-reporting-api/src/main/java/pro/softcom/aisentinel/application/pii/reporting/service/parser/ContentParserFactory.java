package pro.softcom.aisentinel.application.pii.reporting.service.parser;

import lombok.RequiredArgsConstructor;

/**
 * Factory for selecting the appropriate content parser based on content type.
 * <p>
 * Supports automatic detection of HTML vs plain text content using heuristics.
 */
@RequiredArgsConstructor
public class ContentParserFactory {
    
    private final PlainTextParser plainTextParser;
    private final HtmlContentParser htmlContentParser;
    
    /**
     * Gets the appropriate parser for the given source content.
     * Uses automatic detection to determine if content is HTML or plain text.
     * 
     * @param source the source content to analyze
     * @return the appropriate parser for the content type
     */
    public ContentParser getParser(String source) {
        return isHtml(source) ? htmlContentParser : plainTextParser;
    }
    
    /**
     * Gets a parser for a specific content type.
     * 
     * @param type the content type
     * @return the parser for that content type
     */
    public ContentParser getParser(ContentType type) {
        return switch(type) {
            case HTML -> htmlContentParser;
            case PLAIN_TEXT -> plainTextParser;
            case AUTO -> plainTextParser; // Default to plain text if auto with no source
        };
    }
    
    /**
     * Detects if the source content is HTML using simple heuristics.
     * <p>
     * Detection criteria:
     * - Content contains angle brackets (< and >)
     * - Content contains common HTML block tags (p, div, br, html, body)
     * 
     * @param source the source content to analyze
     * @return true if content appears to be HTML, false otherwise
     */
    private boolean isHtml(String source) {
        if (source == null || source.length() < 10) {
            return false;
        }
        
        // Sample first 1000 characters for performance
        String sample = source.substring(0, Math.min(1000, source.length()));
        
        // Check for angle brackets (basic HTML marker)
        boolean hasAngleBrackets = sample.contains("<") && sample.contains(">");
        if (!hasAngleBrackets) {
            return false;
        }
        
        // Check for common HTML block tags

        return sample.contains("<p")
                            || sample.contains("<div")
                            || sample.contains("<br")
                            || sample.contains("<html")
                            || sample.contains("<body")
                            || sample.contains("<h1")
                            || sample.contains("<h2")
                            || sample.contains("<li")
                            || sample.contains("<table");
    }
}
