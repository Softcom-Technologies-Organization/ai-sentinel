package pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in;

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
import pro.softcom.aisentinel.application.pii.remediation.port.in.ChangeFindingStatusPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ExecuteObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.FindingStatusChangeResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.PlanObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.QueryRemediationFindingsPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.RemediationFindingsResult;
import pro.softcom.aisentinel.application.pii.remediation.port.in.TrackObfuscationJobPort;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationJobNotFoundException;
import pro.softcom.aisentinel.domain.pii.remediation.ObfuscationPlan;
import pro.softcom.aisentinel.domain.pii.remediation.RemediationSelection;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.FindingStatusChangeResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobCreatedDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationJobStatusDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.ObfuscationPlanDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationConfigDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSearchResponseDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.RemediationSelectionDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.dto.SelectionStatusChangeRequestDto;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationDtoMapper;
import pro.softcom.aisentinel.infrastructure.pii.remediation.adapter.in.mapper.RemediationJobDtoMapper;

import java.security.Principal;

/**
 * REST controller for PII remediation: server-computed findings view, finding
 * lifecycle transitions, and planning/execution/polling of bulk redaction jobs.
 * Every endpoint except {@code GET /config} sits behind the
 * {@code pii.remediation.enabled} feature flag; the use cases re-check it
 * (defense in depth).
 */
@RestController
@RequestMapping("/api/v1/pii/remediation")
@Tag(name = "PII Remediation", description = "Bulk redaction of detected PII and finding lifecycle")
@RequiredArgsConstructor
@Slf4j
public class PiiRemediationController {

    private static final String SYSTEM_ACTOR = "system";

    private final QueryRemediationFindingsPort queryRemediationFindingsPort;
    private final ChangeFindingStatusPort changeFindingStatusPort;
    private final PlanObfuscationPort planObfuscationPort;
    private final ExecuteObfuscationPort executeObfuscationPort;
    private final TrackObfuscationJobPort trackObfuscationJobPort;
    private final RemediationDtoMapper mapper;
    private final RemediationJobDtoMapper jobMapper;

    @GetMapping("/config")
    @Operation(summary = "Returns whether the remediation feature is enabled")
    @ApiResponse(responseCode = "200", description = "Configuration returned")
    public ResponseEntity<@NonNull RemediationConfigDto> getConfig() {
        return ResponseEntity.ok(new RemediationConfigDto(queryRemediationFindingsPort.isRemediationEnabled()));
    }

    @PostMapping("/findings/search")
    @Operation(summary = "Searches findings grouped and paginated server-side, with the selection resolved")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Findings view returned")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    public ResponseEntity<@NonNull RemediationSearchResponseDto> searchFindings(
            @RequestBody RemediationSearchRequestDto request) {
        RemediationFindingsResult result = queryRemediationFindingsPort.search(mapper.toQuery(request));
        return ResponseEntity.ok(mapper.toDto(result));
    }

    @PostMapping("/findings/status")
    @Operation(summary = "Applies a batch of finding lifecycle transitions")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Batch processed; each change applied or rejected")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    public ResponseEntity<@NonNull FindingStatusChangeResponseDto> changeFindingStatuses(
            @RequestBody FindingStatusChangeRequestDto request, Principal principal) {
        String actor = principal != null ? principal.getName() : SYSTEM_ACTOR;
        FindingStatusChangeResult result = changeFindingStatusPort.changeStatuses(
                mapper.toCommand(request, actor));
        log.info("[PII_REMEDIATION] Status batch by actor={}: {} applied, {} rejected",
                actor, result.applied().size(), result.rejected().size());
        return ResponseEntity.ok(mapper.toDto(result));
    }

    @PostMapping("/findings/status/by-selection")
    @Operation(summary = "Transitions every PENDING finding of a selection to a target status")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Selection resolved and transitioned")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    public ResponseEntity<@NonNull FindingStatusChangeResponseDto> changeFindingStatusesBySelection(
            @RequestBody SelectionStatusChangeRequestDto request, Principal principal) {
        String actor = principal != null ? principal.getName() : SYSTEM_ACTOR;
        RemediationSelection selection = jobMapper.toSelection(request.selection());
        FindingStatusChangeResult result = changeFindingStatusPort.changeStatusesBySelection(
                mapper.toSelectionCommand(selection, request.targetStatus(), actor));
        log.info("[PII_REMEDIATION] Selection status change by actor={}: {} applied, {} rejected",
                actor, result.applied().size(), result.rejected().size());
        return ResponseEntity.ok(mapper.toDto(result));
    }

    @PostMapping("/plan")
    @Operation(summary = "Computes the read-only preview of an obfuscation run for a selection")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Plan computed")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    public ResponseEntity<@NonNull ObfuscationPlanDto> plan(@RequestBody RemediationSelectionDto selection) {
        ObfuscationPlan plan = planObfuscationPort.plan(jobMapper.toSelection(selection));
        return ResponseEntity.ok(jobMapper.toDto(plan));
    }

    @PostMapping("/jobs")
    @Operation(summary = "Submits an asynchronous redaction job for a planned selection")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "202", description = "Job accepted and started")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    @ApiResponse(responseCode = "409", description = "Selection outdated or a job is already running for the space")
    public ResponseEntity<@NonNull ObfuscationJobCreatedDto> submitJob(
            @RequestBody ObfuscationJobRequestDto request, Principal principal) {
        String actor = principal != null ? principal.getName() : SYSTEM_ACTOR;
        String jobId = executeObfuscationPort.execute(jobMapper.toSubmission(request, actor));
        log.info("[PII_REMEDIATION] Redaction job {} submitted by actor={}", jobId, actor);
        return ResponseEntity.accepted().body(new ObfuscationJobCreatedDto(jobId));
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Returns the status, progression and outcomes of a redaction job")
    @PreAuthorize("@environment.getProperty('pii.remediation.enabled', 'false') == 'true'")
    @ApiResponse(responseCode = "200", description = "Job returned")
    @ApiResponse(responseCode = "403", description = "Remediation disabled by configuration")
    @ApiResponse(responseCode = "404", description = "Unknown job")
    public ResponseEntity<@NonNull ObfuscationJobStatusDto> getJob(@PathVariable("id") String id) {
        return trackObfuscationJobPort.findJob(id)
                .map(job -> ResponseEntity.ok(jobMapper.toDto(job)))
                .orElseThrow(() -> new ObfuscationJobNotFoundException(id));
    }
}
