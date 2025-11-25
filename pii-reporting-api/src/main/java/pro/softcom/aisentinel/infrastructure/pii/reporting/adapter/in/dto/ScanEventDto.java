package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.reporting.DetectedPersonallyIdentifiableInformation;

/**
 * Payload for Server-Sent Events emitted during a Confluence space scan.
 * Business intent: provide a stable, typed contract for clients instead of a generic map.
 * Only non-null fields are serialized to keep the wire format compatible with the previous Map-based payloads.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ScanEventDto(
        String scanId,
        String spaceKey,
        ScanEventType eventType,
        Boolean isFinal,
        Integer pagesTotal,
        Integer pageIndex,
        String pageId,
        String pageTitle,
        List<DetectedPersonallyIdentifiableInformation> detectedEntities,
        Map<String, Integer> summary,
        String message,
        String pageUrl,
        String emittedAt,
        String attachmentName,
        String attachmentType,
        String attachmentUrl,
        Double analysisProgressPercentage,
        String status
) { }
