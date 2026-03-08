package pro.softcom.aisentinel.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.ScanStatus;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record ContentScanResult(
    String scanId,
    @JsonAlias("spaceKey") String sourceId,
    String eventType,
    Boolean isFinal,
    @JsonAlias("pagesTotal") Integer contentTotal,
    @JsonAlias("pageIndex") Integer contentIndex,
    @JsonAlias("pageId") String contentId,
    @JsonAlias("pageTitle") String contentTitle,
    List<DetectedPersonallyIdentifiableInformation> detectedPIIList,
    Map<String, Integer> nbOfDetectedPIIBySeverity,
    Map<String, Integer> nbOfDetectedPIIByType,
    @JsonIgnore String sourceContent,
    String maskedContent,
    String message,
    @JsonAlias("pageUrl") String contentUrl,
    String emittedAt,
    String attachmentName,
    String attachmentType,
    String attachmentUrl,
    Double analysisProgressPercentage,
    ScanStatus scanStatus,
    PersonallyIdentifiableInformationSeverity severity
) { }
