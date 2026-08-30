package pro.softcom.aisentinel.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorRunStat;

import java.util.List;
import java.util.Map;

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
    List<DetectedPersonallyIdentifiableInformation> detectedPIIs,
    Map<String, Integer> detectedPiiCountBySeverity,// Severity-based counts (high, medium, low) for badges
    Map<String, Integer> detectedPiiCountByType,// PII type-based counts (EMAIL, CREDIT_CARD, etc.) for item details
    @JsonIgnore String sourceContent,
    String maskedContent,
    // Diagnostic wording for the logs. What the operator reads comes from errorKey below,
    // which the dashboard renders in their language.
    String message,
    String errorKey,
    Map<String, String> errorParams,
    String pageUrl,
    String emittedAt,
    String attachmentName,
    String attachmentType,
    String attachmentUrl,
    Double analysisProgressPercentage,
    ScanStatus scanStatus,
    PersonallyIdentifiableInformationSeverity severity,  // Pre-calculated severity from backend (HIGH/MEDIUM/LOW)
    // Per-detector stats for this item, carried only in-memory through the scan
    // flux for statistics collection. Excluded from the persisted event payload.
    @JsonIgnore List<DetectorRunStat> detectorRunStats
) { }
