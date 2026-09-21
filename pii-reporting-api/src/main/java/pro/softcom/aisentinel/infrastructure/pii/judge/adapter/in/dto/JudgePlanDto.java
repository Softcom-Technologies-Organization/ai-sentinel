package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto;

import java.util.Map;

public record JudgePlanDto(int totalFindings, Map<String, Integer> findingsBySpace) {
}
