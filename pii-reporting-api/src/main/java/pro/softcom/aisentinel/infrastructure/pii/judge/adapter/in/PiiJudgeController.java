package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeEvaluationPort;
import pro.softcom.aisentinel.application.pii.judge.port.in.TrackJudgeJobPort;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobNotFoundException;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeJobDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeLaunchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeLaunchResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgePlanDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeScopeDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.dto.JudgeSpaceSummaryDto;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.in.mapper.JudgeDtoMapper;

import java.security.Principal;
import java.util.List;

/**
 * REST controller of the LLM judge: preview, launch, polling and interruption of judge runs,
 * plus the per-space summary for the dashboard. Judging belongs to the remediation feature and
 * sits behind the same {@code pii.remediation.enabled} flag.
 */
@RestController
@RequestMapping("/api/v1/pii/judge")
@Tag(name = "PII LLM Judge", description = "LLM review of detected PII for false positives")
@RequiredArgsConstructor
@Slf4j
public class PiiJudgeController {

    private static final String SYSTEM_ACTOR = "system";

    private final JudgeEvaluationPort judgeEvaluationPort;
    private final TrackJudgeJobPort trackJudgeJobPort;
    private final JudgeDtoMapper mapper;

    @PostMapping("/plan")
    @Operation(summary = "Counts the pending findings a judge run would evaluate, per space")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Plan computed")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    public ResponseEntity<@NonNull JudgePlanDto> plan(@RequestBody JudgeScopeDto scope) {
        return ResponseEntity.ok(mapper.toDto(judgeEvaluationPort.plan(mapper.toScope(scope))));
    }

    @PostMapping("/jobs")
    @Operation(summary = "Launches one asynchronous judge job per targeted space")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "202", description = "Jobs accepted and started")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    @ApiResponse(responseCode = "409", description = "A judge job is already running for one of the spaces")
    public ResponseEntity<@NonNull JudgeLaunchResponseDto> launch(
            @RequestBody JudgeLaunchRequestDto request, Principal principal) {
        String actor = principal != null ? principal.getName() : SYSTEM_ACTOR;
        List<String> jobIds = judgeEvaluationPort.launch(mapper.toCommand(request, actor));
        log.info("[LLM_JUDGE] {} job(s) submitted by actor={}", jobIds.size(), actor);
        return ResponseEntity.accepted().body(new JudgeLaunchResponseDto(jobIds));
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Returns the status and progression of a judge job")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Job returned")
    @ApiResponse(responseCode = "404", description = "Unknown job")
    public ResponseEntity<@NonNull JudgeJobDto> getJob(@PathVariable("id") String id) {
        return trackJudgeJobPort.findJob(id)
                .map(job -> ResponseEntity.ok(mapper.toDto(job)))
                .orElseThrow(() -> new JudgeJobNotFoundException(id));
    }

    @PostMapping("/jobs/{id}/cancel")
    @Operation(summary = "Interrupts a running judge job; verdicts already recorded are kept")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "202", description = "Interruption requested")
    @ApiResponse(responseCode = "404", description = "Unknown job")
    public ResponseEntity<Void> cancel(@PathVariable("id") String id) {
        trackJudgeJobPort.cancel(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/spaces")
    @Operation(summary = "Per-space judge summary: evaluated findings, proposed false positives, active job")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Summaries returned")
    public ResponseEntity<@NonNull List<JudgeSpaceSummaryDto>> spaceSummaries() {
        return ResponseEntity.ok(trackJudgeJobPort.spaceSummaries().stream().map(mapper::toDto).toList());
    }
}
