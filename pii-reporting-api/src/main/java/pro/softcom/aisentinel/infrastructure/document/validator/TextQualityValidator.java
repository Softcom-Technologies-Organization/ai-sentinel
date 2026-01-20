package pro.softcom.aisentinel.infrastructure.document.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.infrastructure.document.config.TextQualityThresholds;

import java.util.Locale;

/**
 * Validator for human-readable text quality.
 * <p>
 * Business purpose: Determines if extracted text represents valid human-readable content
 * versus corrupted, scanned, or image-only content. Independent of extraction tool.
 * <p>
 * Applies 5 validation rules to assess text quality. Each rule is implemented as a
 * separate method to maintain low cyclomatic complexity (max 5 per method).
 */
@Slf4j
@Component
public class TextQualityValidator {

    private final TextQualityThresholds thresholds;

    public TextQualityValidator(TextQualityThresholds thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Determines if text appears to be from an image-only document (scanned PDF without proper OCR).
     * <p>
     * Applies 5 validation rules to assess text quality.
     *
     * @param text The extracted text to analyze
     * @return true if the text appears to be from an image-only document or is of poor quality
     */
    public boolean isImageOnlyDocument(String text) {
        if (StringUtils.isBlank(text)) {
            log.debug("[TEXT_VALIDATION] Text is blank, considering as image-only");
            return true;
        }

        if (isTooShort(text)) {
            log.debug("[TEXT_VALIDATION] Text too short (length={}, threshold={}), considering as image-only",
                    text.length(), thresholds.getMinTextLength());
            return true;
        }

        if (hasLowAlphanumericRatio(text)) {
            long alphanumericCount = text.chars().filter(Character::isLetterOrDigit).count();
            double ratio = alphanumericCount / (double) text.length();
            log.debug("[TEXT_VALIDATION] Low alphanumeric ratio (ratio={}%, threshold={}%), considering as image-only",
                    String.format(Locale.ROOT, "%.2f", ratio * 100),
                    String.format(Locale.ROOT, "%.2f", thresholds.getMinAlphanumericRatio() * 100));
            return true;
        }

        if (hasInsufficientSpacing(text)) {
            long spaceCount = text.chars().filter(ch -> ch == ' ').count();
            double ratio = spaceCount / (double) text.length();
            log.debug("[TEXT_VALIDATION] Insufficient spacing (ratio={}%, threshold={}%), considering as image-only",
                    String.format(Locale.ROOT, "%.2f", ratio * 100),
                    String.format(Locale.ROOT, "%.2f", thresholds.getMinSpaceRatio() * 100));
            return true;
        }

        if (hasTooManyControlCharacters(text)) {
            long printableCount = text.chars().filter(ch -> !Character.isISOControl(ch)).count();
            double ratio = printableCount / (double) text.length();
            log.debug("[TEXT_VALIDATION] Too many control characters (printable ratio={}%, threshold={}%), considering as image-only",
                    String.format(Locale.ROOT, "%.2f", ratio * 100),
                    String.format(Locale.ROOT, "%.2f", thresholds.getMinPrintableRatio() * 100));
            return true;
        }

        if (hasExcessiveSpecialCharacters(text)) {
            long specialCharCount = text.chars()
                    .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                    .count();
            double ratio = specialCharCount / (double) text.length();
            log.debug("[TEXT_VALIDATION] Excessive special characters (ratio={}%, threshold={}%), considering as image-only",
                    String.format(Locale.ROOT, "%.2f", ratio * 100),
                    String.format(Locale.ROOT, "%.2f", thresholds.getMaxSpecialCharRatio() * 100));
            return true;
        }

        log.debug("[TEXT_VALIDATION] Text passed all quality checks (length={})", text.length());
        return false;
    }

    /**
     * Rule 1: Checks if text is too short (likely OCR garbage or artifacts).
     */
    public boolean isTooShort(String text) {
        return text.length() < thresholds.getMinTextLength();
    }

    /**
     * Rule 2: Checks if text has insufficient alphanumeric characters (likely corrupted).
     */
    public boolean hasLowAlphanumericRatio(String text) {
        long alphanumericCount = text.chars().filter(Character::isLetterOrDigit).count();
        double alphanumericRatio = alphanumericCount / (double) text.length();
        return alphanumericRatio < thresholds.getMinAlphanumericRatio();
    }

    /**
     * Rule 3: Checks if text lacks proper word spacing (likely OCR failure).
     * Natural text contains roughly 12-20% spaces.
     */
    public boolean hasInsufficientSpacing(String text) {
        if (text.length() <= thresholds.getMinLengthForSpaceCheck()) {
            return false;
        }
        long spaceCount = text.chars().filter(ch -> ch == ' ').count();
        double spaceRatio = spaceCount / (double) text.length();
        return spaceRatio < thresholds.getMinSpaceRatio();
    }

    /**
     * Rule 4: Checks if text contains too many control characters (likely corrupted).
     * Uses Character.isISOControl() to detect non-printable control characters.
     */
    public boolean hasTooManyControlCharacters(String text) {
        long printableCount = text.chars().filter(ch -> !Character.isISOControl(ch)).count();
        double printableRatio = printableCount / (double) text.length();
        return printableRatio < thresholds.getMinPrintableRatio();
    }

    /**
     * Rule 5: Checks if text has excessive special characters (likely OCR artifacts).
     */
    public boolean hasExcessiveSpecialCharacters(String text) {
        long specialCharCount = text.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                .count();
        double specialCharRatio = specialCharCount / (double) text.length();
        return specialCharRatio > thresholds.getMaxSpecialCharRatio();
    }
}
