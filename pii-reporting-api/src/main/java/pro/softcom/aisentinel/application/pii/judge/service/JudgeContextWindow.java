package pro.softcom.aisentinel.application.pii.judge.service;

/**
 * Bounds the text handed to the judge around the judged value. The stored context is the
 * whole source line, which on a Confluence page can be a paragraph of thousands of characters;
 * the benchmark showed the verdict needs the surroundings, not the document.
 */
final class JudgeContextWindow {

    static final int SIDE_CHARS = 300;

    private JudgeContextWindow() {
    }

    static String around(String context, String value) {
        if (value == null) {
            return context == null ? "" : head(context);
        }
        if (context == null) {
            return value;
        }
        int start = context.indexOf(value);
        if (start < 0) {
            return head(context);
        }
        int from = Math.max(0, start - SIDE_CHARS);
        int to = Math.min(context.length(), start + value.length() + SIDE_CHARS);
        return context.substring(from, to);
    }

    private static String head(String context) {
        return context.substring(0, Math.min(context.length(), 2 * SIDE_CHARS));
    }
}
