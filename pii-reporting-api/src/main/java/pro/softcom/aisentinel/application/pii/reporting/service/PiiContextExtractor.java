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
        return extractLineContext(source, start, end, type, null, true);
    }

    /**
     * Extracts context while masking all PII occurrences present in the same line as the principal one.
     * Useful when the source contains multiple PIIs to avoid leaking others in the context.
     */
    public String extractMaskedContext(String source, int start, int end, String type, List<DetectedPersonallyIdentifiableInformation> allEntities) {
        return extractLineContext(source, start, end, type, allEntities, true);
    }

    /**
     * Extracts real context without masking PII values.
     * Used for encrypted storage and reveal functionality.
     * The real context contains actual sensitive data and should always be encrypted.
     */
    public String extractSensitiveContext(String source, int start, int end) {
        return extractLineContext(source, start, end, null, null, false);
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

        // Extract masked context for immediate display
        String maskedContext = extractMaskedContext(
                source, entity.startPosition(), entity.endPosition(), entity.piiType(), allEntities
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
     *
     * @param source      complete source content
     * @param start       PII start position
     * @param end         PII end position
     * @param type        detected PII type (can be null if maskPii is false)
     * @param allEntities all entities to mask in the same line (can be null)
     * @param maskPii     whether to mask PII values with tokens
     * @return extracted and truncated context, or null if extraction is not possible
     */
    private String extractLineContext(String source, int start, int end, String type,
                                      List<DetectedPersonallyIdentifiableInformation> allEntities, boolean maskPii) {
        if (source == null || source.isBlank()) {
            return null;
        }

        // Detect content type and get appropriate parser
        ContentParser parser = parserFactory.getParser(source);

        int lineStartInSource = parser.findLineStart(source, Math.clamp(start, 0, source.length()));
        int lineEndInSource = parser.findLineEnd(source, Math.clamp(end, 0, source.length()));
        String lineContext = source.substring(lineStartInSource, lineEndInSource);

        // Apply masking if requested
        if (maskPii) {
            lineContext = maskLineWithEntities(lineContext, lineStartInSource, start, end, type, allEntities);
        }

        // Note: HTML cleaning is now done BEFORE detection in AbstractStreamConfluenceScanUseCase,
        // so the sourceContent passed here is already clean text.

        // Le masquage repose uniquement sur les positions start/end fournies par le détecteur.
        // Aucun ajustement heuristique supplémentaire n'est appliqué sur le suffixe.
        return lineContext;
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
        relEntities.add(new TempEntity(
                Math.clamp((long) mainStart - lineStartInSource, 0, lineLen),
                Math.clamp((long) mainEnd - lineStartInSource, 0, lineLen),
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
