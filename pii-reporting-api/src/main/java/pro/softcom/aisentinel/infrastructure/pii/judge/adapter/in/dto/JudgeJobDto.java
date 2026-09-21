package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Polling view of a judge job: progression counters only, never a value nor a verdict detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeJobDto(
        String jobId,
        String spaceKey,
        String status,
        String model,
        String applyMode,
        double confidenceThreshold,
        int processed,
        int total,
        int falsePositives,
        int errors
) {
}
