package pro.softcom.aisentinel.infrastructure.document.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable thresholds for text quality assessment.
 * <p>
 * Business purpose: Defines thresholds for detecting valid human-readable text
 * versus corrupted, scanned, or image-only content. This is independent of the extraction
 * tool (Tika, PDFBox, etc.) and focuses on the quality of extracted text.
 * <p>
 * Can be used across different document sources (Confluence, SharePoint, Google Drive, etc.)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "document.text-quality")
public class TextQualityThresholds {

    /**
     * Minimum text length (in characters) to consider document as having valid text.
     * Default: 50 characters
     * <p>
     * Documents with less text are considered image-only or OCR garbage.
     */
    private int minTextLength = 50;

    /**
     * Minimum ratio of alphanumeric characters to total characters.
     * Default: 0.05 (5%)
     * <p>
     * Text with less than this ratio is considered corrupted or non-textual.
     */
    private double minAlphanumericRatio = 0.05;

    /**
     * Minimum ratio of spaces to total characters (for text longer than minLengthForSpaceCheck).
     * Default: 0.02 (2%)
     * <p>
     * Natural text contains roughly 12-20% spaces.
     * Less than this suggests OCR failure or concatenated text.
     */
    private double minSpaceRatio = 0.02;

    /**
     * Minimum ratio of printable characters to total characters.
     * Default: 0.80 (80%)
     * <p>
     * Text with many control characters or non-printable chars is likely corrupted.
     */
    private double minPrintableRatio = 0.80;

    /**
     * Maximum ratio of special characters (non-alphanumeric, non-whitespace) to total.
     * Default: 0.40 (40%)
     * <p>
     * Excessive special characters suggest OCR artifacts.
     */
    private double maxSpecialCharRatio = 0.40;

    /**
     * Minimum text length to apply space ratio rule.
     * Default: 100 characters
     * <p>
     * Space ratio check is only meaningful for longer texts.
     */
    private int minLengthForSpaceCheck = 100;
}
