package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.config.PiiContextProperties;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
@Component
@RequiredArgsConstructor
public class PiiContextExtractor {

    private final ContentParserFactory parserFactory;
    private final PiiContextProperties contextProperties;

    public String extract(String source, int start, int end, String type) {
        return extractLineContext(source, start, end, type, null, true);
    }

    /**
     * Extracts context while masking all PII occurrences present in the same line as the principal one.
     * Useful when the source contains multiple PIIs to avoid leaking others in the context.
     */
    public String extract(String source, int start, int end, String type, List<PiiEntity> allEntities) {
        return extractLineContext(source, start, end, type, allEntities, true);
    }

    /**
     * Extracts real context without masking PII values.
     * Used for encrypted storage and reveal functionality.
     * The real context contains actual sensitive data and should always be encrypted.
     */
    public String extractReal(String source, int start, int end) {
        return extractLineContext(source, start, end, null, null, false);
    }

    /**
     * Enrich the given scan result by filling missing context for each PII entity.
     * Returns the original result if enrichment is not applicable.
     * All PIIs in the same line are masked to prevent data leakage.
     */
    public ScanResult enrichContexts(ScanResult scanResult) {
        if (!needsEnrichment(scanResult)) {
            return scanResult;
        }

        try {
            List<PiiEntity> allEntities = scanResult.detectedEntities();
            List<PiiEntity> enriched = allEntities.stream()
                    .map(entity -> enrichEntity(scanResult.sourceContent(), entity, allEntities))
                    .toList();

            return scanResult.toBuilder().detectedEntities(enriched).build();
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Invalid input for PII context enrichment, scanId={}",
                    scanResult.scanId(), e);
            return scanResult;
        } catch (IndexOutOfBoundsException e) {
            log.error("Position error during context extraction, scanId={}",
                    scanResult.scanId(), e);
            return scanResult;
        } catch (Exception e) {
            log.error("Unexpected error during PII context enrichment, scanId={}",
                    scanResult.scanId(), e);
            return scanResult;
        }
    }

    private boolean needsEnrichment(ScanResult scanResult) {
        return scanResult != null
                && scanResult.detectedEntities() != null
                && !scanResult.detectedEntities().isEmpty()
                && scanResult.sourceContent() != null
                && !scanResult.sourceContent().isBlank();
    }

    private PiiEntity enrichEntity(String source, PiiEntity entity, List<PiiEntity> allEntities) {
        if (entity == null || hasContext(entity)) {
            return entity;
        }

        // Extract masked context for immediate display
        String maskedContext = extract(source, entity.startPosition(), entity.endPosition(),
                entity.piiType(), allEntities);
        
        // Extract real context for encrypted storage
        String realContext = extractReal(source, entity.startPosition(), entity.endPosition());
        
        return entity.toBuilder()
                .context(realContext)
                .maskedContext(maskedContext)
                .build();
    }

    private boolean hasContext(PiiEntity entity) {
        return (entity.context() != null && !entity.context().isBlank()) 
                || (entity.maskedContext() != null && !entity.maskedContext().isBlank());
    }

    /**
     * Package-private method for testing purposes.
     * Extracts masked line context with type information.
     */
    String extractMaskedLineContext(String source, int start, int end, String type) {
        return extractLineContext(source, start, end, type, null, true);
    }

    /**
     * Package-private method for testing purposes.
     * Extracts masked line context with type information and entity list.
     */
    String extractMaskedLineContext(String source, int start, int end, String type, List<PiiEntity> allEntities) {
        return extractLineContext(source, start, end, type, allEntities, true);
    }

    /**
     * Unified method to extract line context, with optional masking.
     * 
     * @param source complete source content
     * @param start PII start position
     * @param end PII end position
     * @param type detected PII type (can be null if maskPii is false)
     * @param allEntities all entities to mask in the same line (can be null)
     * @param maskPii whether to mask PII values with tokens
     * @return extracted and truncated context, or null if extraction is not possible
     */
    private String extractLineContext(String source, int start, int end, String type, 
                                     List<PiiEntity> allEntities, boolean maskPii) {
        if (source == null || source.isBlank()) {
            return null;
        }

        // Detect content type and get appropriate parser
        ContentParser parser = parserFactory.getParser(source);

        int lineStartInSource = parser.findLineStart(source, Math.clamp(start, 0, source.length()));
        int lineEndInSource = parser.findLineEnd(source, Math.clamp(end, 0, source.length()));
        String lineContext = source.substring(lineStartInSource, lineEndInSource);

        // Clean HTML tags if present
        lineContext = parser.cleanText(lineContext);

        // Calculate position for truncation centering
        int centerPosition = Math.clamp(start - lineStartInSource, 0, lineContext.length());

        // Apply masking if requested
        if (maskPii) {
            MaskResult masked = maskLineWithEntities(lineContext, lineStartInSource, start, end, type, allEntities);
            lineContext = masked.text();
            centerPosition = masked.mainTokenIndex();
        }

        // Truncate around the center position without cutting words
        String truncated = truncateAroundPositionNoWordCut(lineContext, centerPosition);

        // Compact whitespace for cleaner display
        return compactWhitespace(truncated);
    }

    /**
     * Compacts all consecutive whitespace characters into single spaces and trims the result.
     *
     * @param text the text to compact
     * @return the compacted text with single spaces
     */
    private String compactWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private record MaskResult(String text, int mainTokenIndex) {
    }

    private MaskResult maskLineWithEntities(String lineContext,
                                            int lineStartInSource,
                                            int mainStart,
                                            int mainEnd,
                                            String mainType,
                                            List<PiiEntity> allEntities) {
        int lineLen = lineContext.length();
        List<TempEntity> relEntities = collectRelevantEntities(
                lineLen, lineStartInSource, mainStart, mainEnd, mainType, allEntities
        );

        relEntities.sort(Comparator.comparingInt(te -> te.start));
        return buildMaskedText(lineContext, relEntities, mainStart, lineStartInSource);
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
                                                     List<PiiEntity> allEntities) {
        List<TempEntity> relEntities = new ArrayList<>();

        // Always include the main entity
        relEntities.add(new TempEntity(
                Math.clamp(mainStart - lineStartInSource, 0, lineLen),
                Math.clamp(mainEnd - lineStartInSource, 0, lineLen),
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
    private boolean isEntityInLine(PiiEntity entity, int lineStart, int lineLen) {
        int absE = entity.endPosition();
        int absS = entity.startPosition();
        return !(absE <= lineStart || absS >= lineStart + lineLen);
    }

    /**
     * Checks if an entity is the main entity being processed.
     */
    private boolean isMainEntity(PiiEntity entity, int mainStart, int mainEnd) {
        return entity.startPosition() == mainStart && entity.endPosition() == mainEnd;
    }

    /**
     * Creates a TempEntity from a PiiEntity, adjusting positions relative to the line start.
     */
    private TempEntity createTempEntity(PiiEntity entity, int lineStartInSource, int lineLen) {
        long absS = entity.startPosition();
        long absE = entity.endPosition();
        int rs = (int) Math.clamp(absS - lineStartInSource, 0L, lineLen);
        int re = (int) Math.clamp(absE - lineStartInSource, rs, (long) lineLen);
        return new TempEntity(rs, re, entity.piiType(), false);
    }

    /**
     * Builds the masked text by replacing entity ranges with tokens.
     */
    private MaskResult buildMaskedText(String lineContext,
                                       List<TempEntity> sortedEntities,
                                       int mainStart,
                                       int lineStartInSource) {
        int lineLen = lineContext.length();
        StringBuilder out = new StringBuilder(lineLen + 16);
        int idx = 0;
        int mainTokenIndex = -1;

        for (TempEntity te : sortedEntities) {
            int s = te.start;
            int e = te.end;
            if (s > idx) {
                out.append(lineContext, idx, s);
            }
            int tokenPos = out.length();
            out.append(PiiMaskingUtils.token(te.type));
            if (te.isMain && mainTokenIndex < 0) {
                mainTokenIndex = tokenPos;
            }
            idx = Math.max(idx, e);
        }

        if (idx < lineLen) {
            out.append(lineContext, idx, lineLen);
        }

        if (mainTokenIndex < 0) {
            mainTokenIndex = calculateFallbackPosition(mainStart, lineStartInSource, out.length());
        }

        return new MaskResult(out.toString(), mainTokenIndex);
    }

    /**
     * Calculates fallback position for main token index when not found in the masked text.
     */
    private int calculateFallbackPosition(int mainStart, int lineStartInSource, int textLength) {
        return Math.clamp(mainStart - lineStartInSource, 0, textLength);
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

    private String truncateAroundPositionNoWordCut(String text, int centerPosition) {
        int maxLength = contextProperties.getMaxLength();
        int sideLength = contextProperties.getSideLength();

        if (text.length() <= maxLength) {
            return text;
        }
        int safeCenter = Math.clamp(0, centerPosition, text.length());
        int half = sideLength;
        int from = Math.max(0, safeCenter - half);
        int to = Math.min(text.length(), safeCenter + half);

        // Try to extend to word boundaries without exceeding maxLength
        // Extend left to include the beginning of the current word
        while (from > 0 && !Character.isWhitespace(text.charAt(from - 1)) && (to - (from - 1)) <= maxLength) {
            from--;
        }
        // Extend right to include the end of the current word
        while (to < text.length() && !Character.isWhitespace(text.charAt(to)) && ((to + 1) - from) <= maxLength) {
            to++;
        }

        String slice = text.substring(from, to);
        // Add ellipsis if we cut from either side
        if (from > 0) slice = "…" + slice;
        if (to < text.length()) slice = slice + "…";
        return slice;
    }

}
