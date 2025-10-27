package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

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

    /**
     * Maximum length of context to present to the user.
     * Beyond this length, the context is truncated to avoid overloading the interface.
     */
    private static final int MAX_CONTEXT_LENGTH = 200;

    /**
     * Length on each side of the PII during truncation.
     * Allows keeping enough context before and after the masked PII.
     */
    private static final int CONTEXT_SIDE_LENGTH = 80;

    public String extract(String source, int start, int end, String type) {
        return extractMaskedLineContext(source, start, end, type);
    }

    /**
     * Extracts context while masking all PII occurrences present in the same line as the principal one.
     * Useful when the source contains multiple PIIs to avoid leaking others in the context.
     */
    public String extract(String source, int start, int end, String type, List<PiiEntity> allEntities) {
        return extractMaskedLineContext(source, start, end, type, allEntities);
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

        String context = extractMaskedLineContext(source, entity.startPosition(), entity.endPosition(),
                                                 entity.piiType(), allEntities);
        return entity.toBuilder().context(context).build();
    }

    private boolean hasContext(PiiEntity entity) {
        return entity.context() != null && !entity.context().isBlank();
    }

    /**
     * Extracts the line context containing the PII, masks the sensitive value,
     * and truncates if necessary.
     *
     * @param source complete source content
     * @param start  PII start position
     * @param end    PII end position
     * @param type   detected PII type
     * @return masked and truncated context, or null if extraction is not possible
     */
    String extractMaskedLineContext(String source, int start, int end, String type) {
        return extractMaskedLineContext(source, start, end, type, null);
    }

    String extractMaskedLineContext(String source, int start, int end, String type, List<PiiEntity> allEntities) {
        if (source == null || source.isBlank()) {
            return null;
        }

        // Detect content type and get appropriate parser
        ContentParser parser = parserFactory.getParser(source);

        int lineStartInSource = parser.findLineStart(source, PiiMaskingUtils.clamp(start, 0, source.length()));
        int lineEndInSource = parser.findLineEnd(source, PiiMaskingUtils.clamp(end, 0, source.length()));
        String lineContext = source.substring(lineStartInSource, lineEndInSource);

        // Clean HTML tags if present
        lineContext = parser.cleanText(lineContext);

        MaskResult masked = maskLineWithEntities(lineContext, lineStartInSource, start, end, type, allEntities);

        // Truncate around the main token index on the un-compacted string, snapping to word boundaries
        String truncated = truncateAroundPositionNoWordCut(masked.text(), masked.mainTokenIndex());
        // Finally compact whitespace for cleaner display
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

    // --- New helpers for multi-PII masking and word-boundary truncation ---
    private record MaskResult(String text, int mainTokenIndex) {}

    private MaskResult maskLineWithEntities(String lineContext,
                                            int lineStartInSource,
                                            int mainStart,
                                            int mainEnd,
                                            String mainType,
                                            List<PiiEntity> allEntities) {
        int lineLen = lineContext.length();
        List<TempEntity> relEntities = new ArrayList<>();

        // Always include the main entity
        relEntities.add(new TempEntity(
                PiiMaskingUtils.clamp(mainStart - lineStartInSource, 0, lineLen),
                PiiMaskingUtils.clamp(mainEnd - lineStartInSource, 0, lineLen),
                mainType,
                true
        ));

        if (allEntities != null && !allEntities.isEmpty()) {
            for (PiiEntity e : allEntities) {
                if (e == null) continue;
                int absS = e.startPosition();
                int absE = e.endPosition();
                // filter entities that intersect the line
                if (absE <= lineStartInSource || absS >= lineStartInSource + lineLen) continue;
                // skip if same as main (already included)
                if (absS == mainStart && absE == mainEnd) continue;
                int rs = PiiMaskingUtils.clamp(absS - lineStartInSource, 0, lineLen);
                int re = PiiMaskingUtils.clamp(absE - lineStartInSource, rs, lineLen);
                relEntities.add(new TempEntity(rs, re, e.piiType(), false));
            }
        }

        // Sort by start and build masked line
        relEntities.sort(Comparator.comparingInt(te -> te.start));
        StringBuilder out = new StringBuilder(lineLen + 16);
        int idx = 0;
        int mainTokenIndex = -1;
        for (TempEntity te : relEntities) {
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
            // Fallback: place center near mainStart relative position
            mainTokenIndex = Math.min(Math.max(0, mainStart - lineStartInSource), out.length());
        }
        return new MaskResult(out.toString(), mainTokenIndex);
    }

    private static final class TempEntity {
        final int start;
        final int end;
        final String type;
        final boolean isMain;
        TempEntity(int start, int end, String type, boolean isMain) {
            this.start = start; this.end = end; this.type = type; this.isMain = isMain;
        }
    }

    private String truncateAroundPositionNoWordCut(String text, int centerPosition) {
        if (text.length() <= MAX_CONTEXT_LENGTH) {
            return text;
        }
        int safeCenter = Math.min(Math.max(centerPosition, 0), text.length());
        int half = CONTEXT_SIDE_LENGTH;
        int from = Math.max(0, safeCenter - half);
        int to = Math.min(text.length(), safeCenter + half);

        // Try to extend to word boundaries without exceeding MAX_CONTEXT_LENGTH
        // Extend left to include the beginning of the current word
        while (from > 0 && !Character.isWhitespace(text.charAt(from - 1)) && (to - (from - 1)) <= MAX_CONTEXT_LENGTH) {
            from--;
        }
        // Extend right to include the end of the current word
        while (to < text.length() && !Character.isWhitespace(text.charAt(to)) && ((to + 1) - from) <= MAX_CONTEXT_LENGTH) {
            to++;
        }

        String slice = text.substring(from, to);
        // Add ellipsis if we cut from either side
        if (from > 0) slice = "…" + slice;
        if (to < text.length()) slice = slice + "…";
        return slice;
    }

}
