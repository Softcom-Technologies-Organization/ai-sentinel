package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanErrorClassifier;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanRunState;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanRemainingPages;
import pro.softcom.aisentinel.domain.pii.reporting.ScanRemainingPagesCalculator;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application use case orchestrating Confluence scans and PII detection. What: encapsulates
 * business/reactive flow away from the web controller. Returns ScanEvent stream that the
 * presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceResumeScanUseCase extends
    AbstractStreamConfluenceScanUseCase implements StreamConfluenceResumeScanPort {

    private final ScanCheckpointRepository scanCheckpointRepository;
    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestratorPort;

    public StreamConfluenceResumeScanUseCase(
        ScanPipelineDependencies dependencies,
        ScanCheckpointRepository scanCheckpointRepository,
        PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestratorPort) {
        super(dependencies);
        this.scanCheckpointRepository = scanCheckpointRepository;
        this.scanExecutionOrchestratorPort = scanExecutionOrchestratorPort;
    }


    @Override
    public Flux<ConfluenceContentScanResult> resumeAllSpaces(String scanId) {
        if (isBlank(scanId)) {
            return Flux.empty();
        }
        // Reconnection to a still-live scan: re-attach to the existing replay sink instead of
        // launching a second concurrent pipeline. Both pipelines would emit item events for the
        // same pages, and severity counts are additive/non-idempotent, so a live resume would
        // double-count. A paused scan has a disposed subscription and is therefore NOT active,
        // so it still follows the real resume path below — exactly the intended behavior.
        if (scanExecutionOrchestratorPort.isScanActive(scanId)) {
            log.info("[RESUME] Scan {} is still live — attaching to existing scan (replay), no new work", scanId);
            return scanExecutionOrchestratorPort.subscribeScan(scanId);
        }

        // Refuse the resume when a detector the operator enabled cannot be reached — checked
        // before the checkpoints are flipped below, so a refused resume leaves the scan PAUSED
        // and resumable once the detector is back.
        List<ConfluenceContentScanResult> preflightFailures = detectorPreflightFailures(scanId, null);
        if (!preflightFailures.isEmpty()) {
            return Flux.fromIterable(preflightFailures);
        }

        // Atomically set PAUSED checkpoints back to RUNNING BEFORE emitting scan events,
        // so the UPSERT guard (which blocks PAUSED → RUNNING from scan events) does not reject them.
        // Wrapped in Mono.fromCallable to run on boundedElastic (JPA is blocking).
        ScanRunState run = new ScanRunState(scanId);
        return Mono.fromCallable(() -> scanCheckpointRepository.resumeAllPausedCheckpoints(scanId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(resumed ->
                log.info("[RESUME] Scan {} — {} checkpoint(s) updated from PAUSED to RUNNING", scanId, resumed))
            .then(Mono.fromCallable(() -> scanCheckpointRepository.findByScan(scanId))
                .subscribeOn(Schedulers.boundedElastic()))
            .flatMapMany(checkpoints -> resumeScopedSpaces(run, checkpoints))
            // An outage during a resume must leave the scan PAUSED again, not RUNNING: the
            // checkpoints were flipped to RUNNING above, so without this the scan would look
            // active forever with nothing progressing.
            .concatWith(Flux.defer(() -> {
                persistPauseIfNeeded(run);
                return Flux.empty();
            }))
            .onErrorResume(exception -> {
                log.error("[USECASE] Error when resuming scan: {}", exception.getMessage(),
                          exception);
                run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
                persistPauseIfNeeded(run);
                return buildErrorScanResultFlux(run, null, exception);
            });
    }

    /**
     * Resumes only the spaces that belong to the scan's persisted scope.
     *
     * <p>The scope is derived from the scan's own checkpoints ({@code findByScan}), so a selected
     * scan resumes exactly its selected spaces — never the whole Confluence base. Spaces of the scope
     * that were never started still have a NOT_STARTED checkpoint (created upfront when the scan
     * scope was initialized), so they are resumed and fully scanned.
     */
    private Flux<ConfluenceContentScanResult> resumeScopedSpaces(ScanRunState run, List<ScanCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            log.warn("[RESUME] Scan {} has no persisted checkpoints — nothing to resume", run.scanId());
            return Flux.empty();
        }
        Set<String> scopedSpaceKeys = checkpoints.stream()
            .map(ScanCheckpoint::spaceKey)
            .collect(Collectors.toSet());
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            .flatMapMany(spaces ->
                             Flux.fromIterable(spaces)
                                 .filter(space -> scopedSpaceKeys.contains(space.key()))
                                 // Same rule as a fresh scan: an outage stops the remaining
                                 // spaces so they keep a resumable checkpoint.
                                 .takeWhile(space -> !run.mustPause())
                                 .concatMap(space -> resumeScanResultFlux(run, space)));
    }

    private Flux<ConfluenceContentScanResult> resumeScanResultFlux(ScanRunState run, ConfluenceSpace space) {
        String scanId = run.scanId();
        try {
            var scanCheckpoint = scanCheckpointRepository.findByScanAndSpace(scanId, space.key())
                .orElse(null);
            Flux<ConfluenceContentScanResult> empty = checkScanCompletionAndGenerateFlux(scanCheckpoint);
            return Objects.requireNonNullElseGet(empty, () -> Mono.fromFuture(
                    confluenceAccessor.getAllPagesInSpace(space.key()))
                .flatMapMany(pages -> {
                    ScanRemainingPages scanRemainingPages =
                        ScanRemainingPagesCalculator.computeScanRemainingPages(pages,
                                                                        scanCheckpoint);
                    if (scanRemainingPages.remaining().isEmpty()) {
                        return Flux.empty();
                    }
                    return runScanFlux(run, space.key(), scanRemainingPages.remaining(),
                                       scanRemainingPages.analyzedOffset(),
                                       scanRemainingPages.originalTotal());
                })
                .onErrorResume(exception -> {
                    log.error("[USECASE] Error when resuming scan of space {}: {}",
                              space.key(), exception.getMessage(), exception);
                    run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
                    return buildErrorScanResultFlux(run, space, exception);
                }));
        } catch (Exception exception) {
            log.error("[USECASE] Error when resuming scan of space {}: {}",
                      space.key(), exception.getMessage(), exception);
            run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
            return buildErrorScanResultFlux(run, space, exception);
        }
    }

    private static Flux<ConfluenceContentScanResult> checkScanCompletionAndGenerateFlux(
        ScanCheckpoint checkpointOptional) {
        if (checkpointOptional != null && checkpointOptional.scanStatus() == ScanStatus.COMPLETED) {
            return Flux.empty();
        }
        return null;
    }

    private static Flux<ConfluenceContentScanResult> buildErrorScanResultFlux(ScanRunState run, ConfluenceSpace space,
                                                                              Throwable exception) {
        TranslatableError error = pausedErrorOr(run, resolveErrorMessage(exception));
        return Flux.just(ConfluenceContentScanResult.builder()
                             .scanId(run.scanId())
                             .spaceKey(space != null ? space.key() : null)
                             .eventType(DetectionReportingEventType.ERROR.getLabel())
                             .message(ScanEventFactory.describeForLogs(error))
                             .errorKey(error.key())
                             .errorParams(error.params())
                             .emittedAt(Instant.now().toString())
                             .build());
    }
}
