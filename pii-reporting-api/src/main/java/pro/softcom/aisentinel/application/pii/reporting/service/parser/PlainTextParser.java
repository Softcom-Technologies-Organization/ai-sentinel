package pro.softcom.aisentinel.application.pii.reporting.service.parser;

/**
 * Parser for plain text content where lines are delimited by newline characters (\n).
 * <p>
 * This is the default parser for standard text content without markup.
 */
public class PlainTextParser implements ContentParser {

    @Override
    public int findLineStart(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        int lineBreakIndex = source.lastIndexOf('\n', safePosition);
        return lineBreakIndex >= 0 ? lineBreakIndex + 1 : 0;
    }

    @Override
    public int findLineEnd(String source, int position) {
        int safePosition = Math.clamp(position, 0, source.length());
        int lineBreakIndex = source.indexOf('\n', safePosition);
        return lineBreakIndex >= 0 ? lineBreakIndex : source.length();
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
