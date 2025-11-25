package pro.softcom.aisentinel.domain.pii.reporting;

import lombok.Builder;

@Builder(toBuilder = true)
public record DetectedPersonallyIdentifiableInformation(
        int startPosition,
        int endPosition,
        String piiType,
        String piiTypeLabel,
        double confidence,
        String sensitiveValue,
        String sensitiveContext,
        String maskedContext
) {}
