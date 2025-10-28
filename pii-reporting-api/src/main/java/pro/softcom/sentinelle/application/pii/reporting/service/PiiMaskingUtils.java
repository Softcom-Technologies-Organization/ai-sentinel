package pro.softcom.sentinelle.application.pii.reporting.service;

import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Small shared masking helpers used by both context extraction and presentation mappers.
 * Responsibility:
 * - Build a stable token from a PII type (e.g., [EMAIL], [UNKNOWN])
 * - Clamp indexes and perform safe substring operations
 * - Build a full masked content by replacing each entity range with its token
 */
public final class PiiMaskingUtils {

    private PiiMaskingUtils() {
    }

    public static String token(String type) {
        String t = (type == null || type.isBlank() || Objects.equals("null", type)) ? "UNKNOWN" : type;
        return "[" + t + "]";
    }

    public static String safeSub(String s, int start, int end) {
        int len = s.length();
        int st = Math.clamp(start, 0, len);
        int en = Math.clamp(end, st, len);
        return s.substring(st, en);
    }

    /**
     * Builds the masked source content by replacing each PII with its type token.
     * The result is truncated to the maximum length if necessary.
     * <p>
     * This method is used to generate a secure preview of the complete content
     * before persistence or display to the user.
     *
     * @param source   the original source content
     * @param entities the list of detected PII entities
     * @param maxLen   the maximum result length (0 = no limit)
     * @return the masked content or null if parameters are invalid
     */
    public static String buildMaskedContent(String source, List<PiiEntity> entities, int maxLen) {
        if (!isValidInput(source, entities)) {
            return null;
        }

        try {
            List<PiiEntity> sorted = sortEntitiesByPosition(entities);
            String masked = maskEntitiesInSource(source, sorted);
            return truncateToLength(masked, maxLen);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convenience overload with a default presentation limit of 5000 characters.
     *
     * @param source   the original source content
     * @param entities the list of detected PII entities
     * @return the masked content or null if parameters are invalid
     */
    public static String buildMaskedContent(String source, List<PiiEntity> entities) {
        return buildMaskedContent(source, entities, 5000);
    }

    /**
     * Validates that both source and entities list are usable for masking.
     *
     * @param source   the source content to validate
     * @param entities the entities list to validate
     * @return true if both parameters are valid, false otherwise
     */
    private static boolean isValidInput(String source, List<PiiEntity> entities) {
        return source != null && !source.isBlank()
                && entities != null && !entities.isEmpty();
    }

    /**
     * Sorts PII entities by their start position in ascending order.
     *
     * @param entities the entities to sort
     * @return a new sorted list of entities
     */
    private static List<PiiEntity> sortEntitiesByPosition(List<PiiEntity> entities) {
        return entities.stream()
                .sorted(Comparator.comparingInt(PiiEntity::startPosition))
                .toList();
    }

    /**
     * Masks all PII entities in the source by replacing their values with type tokens.
     * Entities must be sorted by position before calling this method.
     *
     * @param source         the source content
     * @param sortedEntities the entities sorted by start position
     * @return the source content with all PII values replaced by tokens
     */
    private static String maskEntitiesInSource(String source, List<PiiEntity> sortedEntities) {
        int sourceLength = source.length();
        StringBuilder result = new StringBuilder(sourceLength);
        int currentIndex = 0;

        for (PiiEntity entity : sortedEntities) {
            int start = Math.clamp(entity.startPosition(), 0, sourceLength);
            int end = Math.clamp(entity.endPosition(), start, sourceLength);

            if (start > currentIndex) {
                result.append(safeSub(source, currentIndex, start));
            }
            result.append(token(entity.piiType()));
            currentIndex = end;
        }

        if (currentIndex < sourceLength) {
            result.append(safeSub(source, currentIndex, sourceLength));
        }

        return result.toString();
    }

    /**
     * Truncates content to the specified maximum length, adding an ellipsis if truncated.
     *
     * @param content   the content to truncate
     * @param maxLength the maximum allowed length (0 or negative = no limit)
     * @return the truncated content with ellipsis if needed, or original content
     */
    private static String truncateToLength(String content, int maxLength) {
        if (maxLength <= 0 || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "…";
    }
}
