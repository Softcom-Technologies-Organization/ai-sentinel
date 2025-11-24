package pro.softcom.aisentinel.application.pii.reporting.service.parser;

/**
 * Parser strategy for extracting line context from different content types.
 * <p>
 * Implementations handle specific content formats (plain text, HTML, etc.)
 * and define what constitutes a "line" in that format.
 * <p>
 * For plain text: lines are delimited by \n
 * For HTML: lines are delimited by block-level tags (p, div, br, etc.)
 */
public interface ContentParser {
    
    /**
     * Finds the start position of the logical line containing the given position.
     * 
     * @param source the complete source content
     * @param position the position within the content
     * @return the start index of the line containing the position
     */
    int findLineStart(String source, int position);
    
    /**
     * Finds the end position of the logical line containing the given position.
     * 
     * @param source the complete source content
     * @param position the position within the content
     * @return the end index of the line containing the position
     */
    int findLineEnd(String source, int position);
    
    /**
     * Cleans the extracted text for display purposes.
     * For plain text: returns as-is
     * For HTML: removes HTML tags and converts to readable text
     * 
     * @param text the raw extracted text
     * @return cleaned text ready for display
     */
    String cleanText(String text);
    
    /**
     * Returns the content type handled by this parser.
     * 
     * @return the content type
     */
    ContentType getContentType();
}
