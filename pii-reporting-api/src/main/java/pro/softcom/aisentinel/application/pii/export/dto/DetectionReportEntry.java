package pro.softcom.aisentinel.application.pii.export.dto;

import lombok.Builder;

@Builder
public record DetectionReportEntry(
        String scanId,
        String spaceKey,
        String emittedAt,
        String pageTitle,
        String pageUrl,
        String attachmentName,
        String attachmentUrl,
        String maskedContext,
        String type,
        String typeLabel,
        double confidenceScore
) {
}
