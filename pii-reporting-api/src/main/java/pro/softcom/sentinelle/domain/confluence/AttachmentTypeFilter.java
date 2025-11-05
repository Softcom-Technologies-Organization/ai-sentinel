package pro.softcom.sentinelle.domain.confluence;

import java.util.Collections;
import java.util.Set;

/**
 * Business rules for filtering extractable attachment types.
 *
 * Business purpose: Centrally defines which file types are extractable
 * for PII analysis. This class prevents logic duplication across
 * different application layers (Application and Infrastructure).
 *
 * Supported extensions include common document formats that contain
 * extractable text via Apache Tika.
 */
public final class AttachmentTypeFilter {

    /**
     * Extractable file extensions for PII analysis.
     * Includes common office document and text formats.
     */
    private static final Set<String> EXTRACTABLE_EXTENSIONS = Set.of(
        // PDF
        "pdf",
        // Microsoft Office (legacy versions)
        "doc", "ppt", "xls",
        // Microsoft Office (modern versions)
        "docx", "pptx", "xlsx",
        // Text formats
        "rtf", "txt", "csv",
        // OpenDocument
        "odt", "ods", "odp",
        // Web
        "html", "htm"
    );

    private AttachmentTypeFilter() {
        // Utility class - private constructor
        throw new AssertionError("AttachmentTypeFilter is a utility class and should not be instantiated");
    }

    /**
     * Checks if an attachment is extractable based on its extension.
     *
     * Business rule: An attachment is considered extractable if its extension
     * matches one of the supported document formats. Comparison is case-insensitive.
     *
     * @param attachment Attachment information to verify
     * @return true if attachment is extractable, false otherwise
     */
    public static boolean isExtractable(AttachmentInfo attachment) {
        if (attachment == null || attachment.extension() == null) {
            return false;
        }

        String normalizedExtension = attachment.extension().toLowerCase().trim();
        return EXTRACTABLE_EXTENSIONS.contains(normalizedExtension);
    }

    /**
     * Returns the list of supported extensions.
     * Useful for logging, debugging, and documentation.
     *
     * @return Immutable set of supported extensions
     */
    public static Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(EXTRACTABLE_EXTENSIONS);
    }
}
