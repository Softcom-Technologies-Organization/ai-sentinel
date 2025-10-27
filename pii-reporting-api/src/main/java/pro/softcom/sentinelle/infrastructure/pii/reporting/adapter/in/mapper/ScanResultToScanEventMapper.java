package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.mapper;

import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventDto;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;

import java.util.Comparator;
import java.util.List;

/**
 * Maps domain ScanResult (clean architecture) to presentation ScanEvent (DTO for SSE/JSON).
 * This keeps the domain independent from the web layer while preserving API contract.
 */
@Component
public class ScanResultToScanEventMapper {

    public ScanEventDto toDto(ScanResult scanResult) {
        if (scanResult == null) return null;
        String masked = scanResult.maskedContent();
        if (masked == null) {
            masked = buildMaskedContent(scanResult.sourceContent(), scanResult.detectedEntities());
        }
        return ScanEventDto.builder()
                .scanId(scanResult.scanId())
                .spaceKey(scanResult.spaceKey())
                .eventType(ScanEventType.from(scanResult.eventType()))
                .isFinal(scanResult.isFinal())
                .pagesTotal(scanResult.pagesTotal())
                .pageIndex(scanResult.pageIndex())
                .pageId(scanResult.pageId())
                .pageTitle(scanResult.pageTitle())
                .detectedEntities(scanResult.detectedEntities())
                .summary(scanResult.summary())
                .maskedContent(masked)
                .message(scanResult.message())
                .pageUrl(scanResult.pageUrl())
                .emittedAt(scanResult.emittedAt())
                .attachmentName(scanResult.attachmentName())
                .attachmentType(scanResult.attachmentType())
                .attachmentUrl(scanResult.attachmentUrl())
                .analysisProgressPercentage(scanResult.analysisProgressPercentage())
                .build();
    }

    // --- Masking presenter (adapter-side) ---
    private String buildMaskedContent(String source, List<PiiEntity> entities) {
        if (source == null || source.isBlank()) return null;
        if (entities == null || entities.isEmpty()) return null;
        try {
            int len = source.length();
            StringBuilder sb = new StringBuilder(Math.min(len, 6000));
            int idx = 0;
            var sorted = entities.stream()
                    .sorted(Comparator.comparingInt(PiiEntity::startPosition))
                    .toList();
            for (PiiEntity e : sorted) {
                int start = Math.clamp(toInt(e.startPosition()), 0, len);
                int end = Math.clamp(toInt(e.endPosition()), start, len);
                if (start > idx) {
                    sb.append(safeSub(source, idx, start));
                }
                String type = String.valueOf(e.piiType());
                String token = (type != null && !"null".equalsIgnoreCase(type)) ? type : "UNKNOWN";
                sb.append('[').append(token).append(']');
                idx = end;
            }
            if (idx < len) {
                sb.append(safeSub(source, idx, len));
            }
            return truncate(sb.toString());
        } catch (Exception _) {
            return null;
        }
    }

    private static int toInt(Object o) {
        return switch (o) {
            case null -> 0;
            case Integer i -> i;
            case Long l -> (int) l.longValue();
            case Double d -> d.intValue();
            default -> {
                try {
                    yield Integer.parseInt(String.valueOf(o));
                } catch (NumberFormatException _) {
                    yield 0;
                }
            }
        };
    }


    private static String safeSub(String s, int start, int end) {
        int len = s.length();
        int st = Math.clamp(start, 0, len);
        int en = Math.clamp(end, st, len);
        return s.substring(st, en);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        if (s.length() <= 5000) return s;
        return s.substring(0, 5000) + "…";
    }
}
