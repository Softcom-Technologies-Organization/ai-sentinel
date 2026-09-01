package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body of an on-demand concurrency benchmark run.
 *
 * @param maxConcurrency Highest concurrency level to measure (2..20); the
 *                       service default applies when absent
 */
public record RunConcurrencyBenchmarkRequestDto(
    @JsonProperty("maxConcurrency")
    @Min(value = 2, message = "maxConcurrency must be at least 2")
    @Max(value = 20, message = "maxConcurrency must be at most 20")
    Integer maxConcurrency
) {
}
