package pro.softcom.aisentinel.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.ScanStatus;

@Builder(toBuilder = true)
public record ConfluenceContentScanResult(
    String scanId,
    String spaceKey,
    String eventType,
    Boolean isFinal,
    Integer pagesTotal,
    Integer pageIndex,
    String pageId,
    String pageTitle,
    List<DetectedPersonallyIdentifiableInformation> detectedPIIList,
    Map<String, Integer> nbOfDetectedPIIBySeverity,// Severity-based counts (high, medium, low) for badges
    Map<String, Integer> nbOfDetectedPIIByType,// PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item details
    @JsonIgnore String sourceContent,
    String maskedContent,
    String message,
    String pageUrl,
    String emittedAt,
    String attachmentName,
    String attachmentType,
    String attachmentUrl,
    Double analysisProgressPercentage,
    ScanStatus scanStatus,
    String severity  // Pre-calculated severity from backend (HIGH/MEDIUM/LOW)
) { }
