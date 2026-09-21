package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeLaunchCommand;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgePlan;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeScope;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeSpaceSummary;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeJobDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeLaunchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgePlanDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeSpaceSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationJobDtoMapper;

import java.util.Locale;

/**
 * Maps judge REST DTOs to application port models and back. Invalid enum-like inputs raise
 * {@link IllegalArgumentException}, mapped to 400 by the global handler.
 */
@Component
@RequiredArgsConstructor
public class JudgeDtoMapper {

    static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.8;

    private final RemediationJobDtoMapper selectionMapper;

    public JudgeScope toScope(JudgeScopeDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("scope is required");
        }
        return new JudgeScope(dto.spaceKeys(),
                dto.selection() == null ? null : selectionMapper.toSelection(dto.selection()));
    }

    public JudgeLaunchCommand toCommand(JudgeLaunchRequestDto request, String actor) {
        if (request == null || request.applyMode() == null) {
            throw new IllegalArgumentException("applyMode is required");
        }
        double threshold = request.confidenceThreshold() == null
                ? DEFAULT_CONFIDENCE_THRESHOLD
                : request.confidenceThreshold();
        return new JudgeLaunchCommand(toScope(request.scope()), request.model(),
                JudgeApplyMode.valueOf(request.applyMode().toUpperCase(Locale.ROOT)), threshold, actor);
    }

    public JudgePlanDto toDto(JudgePlan plan) {
        return new JudgePlanDto(plan.totalFindings(), plan.findingsBySpace());
    }

    public JudgeJobDto toDto(JudgeJob job) {
        return new JudgeJobDto(job.id(), job.spaceKey(), job.status().name(), job.model(),
                job.applyMode().name(), job.confidenceThreshold(), job.processed(), job.total(),
                job.falsePositives(), job.errors());
    }

    public JudgeSpaceSummaryDto toDto(JudgeSpaceSummary summary) {
        return new JudgeSpaceSummaryDto(summary.spaceKey(), summary.judgedCount(),
                summary.proposedFalsePositives(),
                summary.activeJob() == null ? null : toDto(summary.activeJob()));
    }
}
