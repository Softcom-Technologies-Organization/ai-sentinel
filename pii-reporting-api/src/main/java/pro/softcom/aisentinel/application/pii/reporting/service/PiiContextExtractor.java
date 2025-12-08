package pro.softcom.aisentinel.application.pii.reporting.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

/**
 * Extracts a readable context around detected PII occurrences.
 * <p>
 * Responsibilities:
 * - Extract the line context containing the PII
 * - Mask the sensitive value with its [TYPE] token
 * - Truncate the context when it is too long for UI display
 * - Idempotent: does not override an existing context
 * <p>
 * MAX_CONTEXT_LENGTH and CONTEXT_SIDE_LENGTH control readability and size.
 */
@Slf4j
@RequiredArgsConstructor
public class PiiContextExtractor {

    private final ContentParserFactory parserFactory;


    public String extractMaskedContext(String source, int start, int end, String type) {
        return extractLineContext(source, start, end, type, null, null, true);
    }

    /**
     * Extracts context while masking all PII occurrences present in the same line as the principal one.
     * Useful when the source contains multiple PIIs to avoid leaking others in the context.
     */
    public String extractMaskedContext(String source, int start, int end, String type, List<DetectedPersonallyIdentifiableInformation> allEntities) {
        return extractLineContext(source, start, end, type, null, allEntities, true);
    }

    /**
     * Extracts masked context using position-as-hints approach.
     * Uses the exact PII value for search-based masking when positions might be approximate.
     *
     * @param source   complete source content
     * @param start    approximate start position (used as hint)
     * @param end      approximate end position (used as hint)
     * @param type     detected PII type
     * @param piiValue exact PII value to search and mask
     * @return extracted and masked context
     */
    public String extractMaskedContext(String source, int start, int end, String type, String piiValue) {
        return extractLineContext(source, start, end, type, piiValue, null, true);
    }

    /**
     * Extracts masked context using position-as-hints approach with multiple entities.
     * Uses the exact PII value for search-based masking when positions might be approximate.
     *
     * @param source      complete source content
     * @param start       approximate start position (used as hint)
     * @param end         approximate end position (used as hint)
     * @param type        detected PII type
     * @param piiValue    exact PII value to search and mask
     * @param allEntities all entities to mask in the same line
     * @return extracted and masked context
     */
    public String extractMaskedContext(String source, int start, int end, String type, String piiValue,
                                       List<DetectedPersonallyIdentifiableInformation> allEntities) {
        return extractLineContext(source, start, end, type, piiValue, allEntities, true);
    }

    /**
     * Extracts real context without masking PII values.
     * Used for encrypted storage and reveal functionality.
     * The real context contains actual sensitive data and should always be encrypted.
     */
    public String extractSensitiveContext(String source, int start, int end) {
        return extractLineContext(source, start, end, null, null, null, false);
    }

    /**
     * Enrich the given scan result by filling missing context for each PII entity.
     * Returns the original result if enrichment is not applicable.
     * All PIIs in the same line are masked to prevent data leakage.
     */
    public ConfluenceContentScanResult enrichContexts(
        ConfluenceContentScanResult confluenceContentScanResult) {
        if (!needsEnrichment(confluenceContentScanResult)) {
            return confluenceContentScanResult;
        }

        try {
            List<DetectedPersonallyIdentifiableInformation> allEntities = confluenceContentScanResult.detectedPIIList();
            List<DetectedPersonallyIdentifiableInformation> enriched = allEntities.stream()
                    .map(entity -> enrichEntity(confluenceContentScanResult.sourceContent(), entity, allEntities))
                    .toList();

            return confluenceContentScanResult.toBuilder().detectedPIIList(enriched).build();
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Invalid input for PII context enrichment, scanId={}",
                     confluenceContentScanResult.scanId(), e);
            return confluenceContentScanResult;
        } catch (IndexOutOfBoundsException e) {
            log.error("Position error during context extraction, scanId={}",
                      confluenceContentScanResult.scanId(), e);
            return confluenceContentScanResult;
        } catch (Exception e) {
            log.error("Unexpected error during PII context enrichment, scanId={}",
                      confluenceContentScanResult.scanId(), e);
            return confluenceContentScanResult;
        }
    }

    private boolean needsEnrichment(ConfluenceContentScanResult confluenceContentScanResult) {
        return confluenceContentScanResult != null
                && confluenceContentScanResult.detectedPIIList() != null
                && !confluenceContentScanResult.detectedPIIList().isEmpty()
                && confluenceContentScanResult.sourceContent() != null
                && !confluenceContentScanResult.sourceContent().isBlank();
    }

    private DetectedPersonallyIdentifiableInformation enrichEntity(String source, DetectedPersonallyIdentifiableInformation entity, List<DetectedPersonallyIdentifiableInformation> allEntities) {
        if (entity == null || hasContext(entity)) {
            return entity;
        }

        // Extract masked context for immediate display using position-as-hints approach
        String maskedContext = extractMaskedContext(
                source, entity.startPosition(), entity.endPosition(), entity.piiType(), 
                entity.sensitiveValue(), allEntities
        );

        // Extract sensitive context for encrypted storage
        String sensitiveContext = extractSensitiveContext(source, entity.startPosition(), entity.endPosition());

        return entity.toBuilder()
                .sensitiveContext(sensitiveContext)
                .maskedContext(maskedContext)
                .build();
    }

    private boolean hasContext(DetectedPersonallyIdentifiableInformation entity) {
        return (entity.sensitiveContext() != null && !entity.sensitiveContext().isBlank())
                || (entity.maskedContext() != null && !entity.maskedContext().isBlank());
    }

    /**
     * Unified method to extract line context, with optional masking.
     * <p>
     * IMPORTANT: The detector calculates positions on CLEANED text (HTML tags removed).
     * Therefore, we must clean the source FIRST before applying positions.
     * Applying positions from cleaned text to raw HTML would produce garbage
     * (e.g., "ac:breakout-width="760" ac:local-id="26967c1d-df67-4").
     * <p>
     * Position-as-Hints Approach:
     * When piiValue is provided, positions are used as approximate location hints rather than exact boundaries.
     * The method searches for the exact piiValue near the hint positions and masks it when found.
     * This handles cases where positions may be slightly off due to cleaning/parsing variations.
     *
     * @param source      complete source content (may contain HTML)
     * @param start       PII start position (calculated on cleaned text, used as hint if piiValue provided)
     * @param end         PII end position (calculated on cleaned text, used as hint if piiValue provided)
     * @param type        detected PII type (can be null if maskPii is false)
     * @param piiValue    exact PII value to search and mask (can be null for position-exact approach)
     * @param allEntities all entities to mask in the same line (can be null)
     * @param maskPii     whether to mask PII values with tokens
     * @return extracted and truncated context, or null if extraction is not possible
     */
    private String extractLineContext(String source, int start, int end, String type, String piiValue,
                                      List<DetectedPersonallyIdentifiableInformation> allEntities, boolean maskPii) {
        if (source == null || source.isBlank()) {
            return null;
        }

        // Detect content type and get appropriate parser
        ContentParser parser = parserFactory.getParser(source);

        // FIX: Clean the source FIRST before applying positions
        // Positions from detector are calculated on cleaned text, not raw HTML
        String cleanedSource = parser.cleanText(source);

        // Apply positions on CLEANED text (matching what detector used)
        int safeStart = Math.clamp(start, 0, cleanedSource.length());
        int safeEnd = Math.clamp(end, 0, cleanedSource.length());

        int lineStartInCleaned = parser.findLineStart(cleanedSource, safeStart);
        int lineEndInCleaned = parser.findLineEnd(cleanedSource, safeEnd);
        String lineContext = cleanedSource.substring(lineStartInCleaned, lineEndInCleaned);

        // Apply masking if requested (positions are now relative to cleaned text)
        if (maskPii) {
            // Use position-as-hints approach if piiValue is provided
            if (piiValue != null && !piiValue.isBlank()) {
                lineContext = maskLineWithPositionHints(lineContext, lineStartInCleaned, safeStart, safeEnd, type, piiValue, allEntities);
            } else {
                lineContext = maskLineWithEntities(lineContext, lineStartInCleaned, safeStart, safeEnd, type, allEntities);
            }
        }

        // No need to clean again - already cleaned
        return lineContext.strip();
    }

    private String maskLineWithEntities(String lineContext,
                                        int lineStartInSource,
                                        int mainStart,
                                        int mainEnd,
                                        String mainType,
                                        List<DetectedPersonallyIdentifiableInformation> allEntities) {
        int lineLen = lineContext.length();
        List<TempEntity> relEntities = collectRelevantEntities(
                lineLen, lineStartInSource, mainStart, mainEnd, mainType, allEntities
        );

        relEntities.sort(Comparator.comparingInt(te -> te.start));
        return buildMaskedText(lineContext, relEntities);
    }

    /**
     * Masks the line using position-as-hints approach.
     * <p>
     * Instead of using positions as exact boundaries, this method uses them as approximate
     * location hints to search for the exact piiValue. This handles cases where positions
     * may be slightly off due to text processing variations.
     * <p>
     * Algorithm:
     * 1. Calculate hint position relative to line start
     * 2. Search for exact piiValue near the hint position
     * 3. If found, create entity at found position
     * 4. Also process all secondary entities
     * 5. Sort and mask all entities
     *
     * @param lineContext      the line text to mask
     * @param lineStartInSource absolute position of line start in source
     * @param hintStart        absolute hint start position (from detector)
     * @param hintEnd          absolute hint end position (from detector)
     * @param mainType         PII type for the main entity
     * @param piiValue         exact PII value to search and mask
     * @param allEntities      all entities to mask in the same line
     * @return masked line text
     */
    private String maskLineWithPositionHints(String lineContext,
                                            int lineStartInSource,
                                            int hintStart,
                                            int hintEnd,
                                            String mainType,
                                            String piiValue,
                                            List<DetectedPersonallyIdentifiableInformation> allEntities) {
        int lineLen = lineContext.length();
        List<TempEntity> relEntities = new ArrayList<>();

        // Calculate hint position relative to line
        int hintPosInLine = Math.max(0, hintStart - lineStartInSource);
        
        // Define search region: ±50 characters around hint position
        // This handles cases where positions are slightly off
        int searchRadius = 50;
        
        // Search for the exact piiValue near the hint position
        int foundPos = findClosestOccurrence(lineContext, piiValue, hintPosInLine, searchRadius);
        
        if (foundPos >= 0) {
            // Found the PII value - create entity at found position
            int foundEnd = foundPos + piiValue.length();
            relEntities.add(new TempEntity(foundPos, foundEnd, mainType, true));
            
            log.debug("Position-as-hints: found '{}' at position {} (hint was {})", 
                     piiValue, foundPos, hintPosInLine);
        } else {
            // Fallback: use hint positions as-is (original behavior)
            int calculatedStart = Math.clamp(hintPosInLine, 0, lineLen);
            int calculatedEnd = Math.clamp(hintEnd - lineStartInSource, calculatedStart, lineLen);
            relEntities.add(new TempEntity(calculatedStart, calculatedEnd, mainType, true));
            
            log.warn("Position-as-hints: could not find '{}' near hint position {}, using hint positions as-is", 
                    piiValue, hintPosInLine);
        }

        // Add secondary entities using the same logic as maskLineWithEntities
        if (allEntities != null && !allEntities.isEmpty()) {
            allEntities.stream()
                    .filter(Objects::nonNull)
                    .filter(e -> isEntityInLine(e, lineStartInSource, lineLen))
                    .filter(e -> !isMainEntity(e, hintStart, hintEnd))
                    .forEach(e -> relEntities.add(createTempEntity(e, lineStartInSource, lineLen)));
        }

        relEntities.sort(Comparator.comparingInt(te -> te.start));
        return buildMaskedText(lineContext, relEntities);
    }

    /**
     * Finds the closest occurrence of a value near a hint position.
     * <p>
     * When multiple occurrences exist, returns the one closest to the hint position.
     * This implements the "position-as-hints" strategy where positions are approximate
     * location indicators rather than exact boundaries.
     *
     * @param text              the text to search in
     * @param searchValue       the exact value to find
     * @param hintPosition      approximate position where value is expected
     * @param searchRegionRadius how many characters to search on each side of hint
     * @return position of closest occurrence, or -1 if not found
     */
    private int findClosestOccurrence(String text, String searchValue, int hintPosition, int searchRegionRadius) {
        if (text == null || text.isEmpty() || searchValue == null || searchValue.isEmpty()) {
            return -1;
        }

        // Define search region boundaries
        int searchStart = Math.max(0, hintPosition - searchRegionRadius);
        int searchEnd = Math.min(text.length(), hintPosition + searchRegionRadius + searchValue.length());
        
        // Extract search region
        String searchRegion = text.substring(searchStart, searchEnd);
        
        // Find all occurrences in the search region
        List<Integer> occurrences = new ArrayList<>();
        int pos = 0;
        while ((pos = searchRegion.indexOf(searchValue, pos)) >= 0) {
            occurrences.add(searchStart + pos);
            pos++;
        }
        
        if (occurrences.isEmpty()) {
            return -1;
        }
        
        // Find the occurrence closest to the hint position
        int closestPos = occurrences.getFirst();
        int minDistance = Math.abs(closestPos - hintPosition);
        
        for (int occurrence : occurrences) {
            int distance = Math.abs(occurrence - hintPosition);
            if (distance < minDistance) {
                minDistance = distance;
                closestPos = occurrence;
            }
        }
        
        return closestPos;
    }

    /**
     * Collects all relevant entities for masking: the main entity and secondary entities
     * that intersect with the current line.
     */
    private List<TempEntity> collectRelevantEntities(int lineLen,
                                                     int lineStartInSource,
                                                     int mainStart,
                                                     int mainEnd,
                                                     String mainType,
                                                     List<DetectedPersonallyIdentifiableInformation> allEntities) {
        List<TempEntity> relEntities = new ArrayList<>();

        // Always include the main entity
        // FIX: Use calculated start position as minimum for end position to prevent trailing characters
        int calculatedStart = (int) Math.clamp((long) mainStart - lineStartInSource, 0L, lineLen);
        relEntities.add(new TempEntity(
                calculatedStart,
                (int) Math.clamp((long) mainEnd - lineStartInSource, calculatedStart, (long) lineLen),
                mainType,
                true
        ));

        // Add secondary entities using stream with filters (eliminates continue statements)
        if (allEntities != null && !allEntities.isEmpty()) {
            allEntities.stream()
                    .filter(Objects::nonNull)
                    .filter(e -> isEntityInLine(e, lineStartInSource, lineLen))
                    .filter(e -> !isMainEntity(e, mainStart, mainEnd))
                    .forEach(e -> relEntities.add(createTempEntity(e, lineStartInSource, lineLen)));
        }

        return relEntities;
    }

    /**
     * Checks if an entity intersects with the current line.
     */
    private boolean isEntityInLine(DetectedPersonallyIdentifiableInformation entity, int lineStart, int lineLen) {
        int absE = entity.endPosition();
        int absS = entity.startPosition();
        return !(absE <= lineStart || absS >= lineStart + lineLen);
    }

    /**
     * Checks if an entity is the main entity being processed.
     */
    private boolean isMainEntity(DetectedPersonallyIdentifiableInformation entity, int mainStart, int mainEnd) {
        return entity.startPosition() == mainStart && entity.endPosition() == mainEnd;
    }

    /**
     * Creates a TempEntity from a PiiEntity, adjusting positions relative to the line start.
     * FIX: Use calculated start position (rs) as minimum for end position to prevent trailing characters.
     */
    private TempEntity createTempEntity(DetectedPersonallyIdentifiableInformation entity, int lineStartInSource, int lineLen) {
        long absS = entity.startPosition();
        long absE = entity.endPosition();
        int rs = (int) Math.clamp(absS - lineStartInSource, 0L, lineLen);
        int re = (int) Math.clamp(absE - lineStartInSource, rs, (long) lineLen);
        return new TempEntity(rs, re, entity.piiType(), false);
    }

    /**
     * Builds the masked text by replacing entity ranges with tokens.
     */
    private String buildMaskedText(String lineContext, List<TempEntity> sortedEntities) {
        int lineLen = lineContext.length();
        StringBuilder out = new StringBuilder(lineLen + 16);
        int idx = 0;
        for (TempEntity te : sortedEntities) {
            int s = te.start;
            int e = te.end;
            if (s > idx) {
                out.append(lineContext, idx, s);
            }
            out.append(PiiMaskingUtils.token(te.type));
            idx = Math.max(idx, e);
        }

        if (idx < lineLen) {
            out.append(lineContext, idx, lineLen);
        }

        return out.toString();
    }

    private static final class TempEntity {
        final int start;
        final int end;
        final String type;
        final boolean isMain;

        TempEntity(int start, int end, String type, boolean isMain) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.isMain = isMain;
        }
    }
}
