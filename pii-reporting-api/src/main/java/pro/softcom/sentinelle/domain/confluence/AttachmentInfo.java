package pro.softcom.sentinelle.domain.confluence;

/**
 * Basic information about a Confluence page attachment.
 * Business-oriented value object used to log and validate retrieval.
 */
public record AttachmentInfo(
        String name,
        String extension,
        String mimeType,
        String url
) {
    /**
     * Returns the file extension or an empty string when no extension is present.
     */
    public static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            return ""; // no extension or hidden file without extension
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
