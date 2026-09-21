package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto;

import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSelectionDto;

import java.util.List;

/**
 * Scope of a judge run: whole spaces (dashboard) or one selection inside a space (remediation).
 */
public record JudgeScopeDto(List<String> spaceKeys, RemediationSelectionDto selection) {
}
