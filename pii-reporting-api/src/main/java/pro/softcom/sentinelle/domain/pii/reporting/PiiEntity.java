package pro.softcom.sentinelle.domain.pii.reporting;

import lombok.Builder;

@Builder(toBuilder = true)
public record PiiEntity(
        int startPosition,
        int endPosition,
        String piiType,
        String piiTypeLabel,
        double confidence,
        String sensitiveValue,
        String sensitiveContext,
        String maskedContext
) {}
