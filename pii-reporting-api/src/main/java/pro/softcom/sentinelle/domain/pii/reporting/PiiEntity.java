package pro.softcom.sentinelle.domain.pii.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder(toBuilder = true)
public record PiiEntity(
        int start,
        int end,
        String type,
        @JsonProperty("typeLabel")
        String typeLabel,
        double score,
        String text,
        @JsonProperty("context")
        String context
) {}
