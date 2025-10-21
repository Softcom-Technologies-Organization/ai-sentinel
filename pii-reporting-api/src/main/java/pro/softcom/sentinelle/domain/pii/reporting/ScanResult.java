package pro.softcom.sentinelle.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record ScanResult(
        String scanId,
        String spaceKey,
        String eventType,
        Boolean isFinal,
        Integer pagesTotal,
        Integer pageIndex,
        String pageId,
        String pageTitle,
        List<PiiEntity> entities,
        Map<String, Integer> summary,
        @JsonIgnore String sourceContent,
        String maskedContent,
        String message,
        String pageUrl,
        String emittedAt,
        String attachmentName,
        String attachmentType,
        String attachmentUrl,
        Double analysisProgressPercentage
) { }
