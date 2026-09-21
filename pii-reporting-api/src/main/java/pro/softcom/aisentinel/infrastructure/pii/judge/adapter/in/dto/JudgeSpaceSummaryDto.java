package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeSpaceSummaryDto(
        String spaceKey,
        long judgedCount,
        long proposedFalsePositives,
        JudgeJobDto activeJob
) {
}
