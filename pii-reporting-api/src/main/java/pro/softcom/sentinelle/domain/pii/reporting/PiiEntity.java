package pro.softcom.sentinelle.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record PiiEntity(
        int startPosition,
        int endPosition,
        String piiType,
        @JsonProperty("piiTypeLabel")
        String piiTypeLabel,
        double confidence,
        String detectedValue,
        @JsonProperty("context")
        String context
) {}
