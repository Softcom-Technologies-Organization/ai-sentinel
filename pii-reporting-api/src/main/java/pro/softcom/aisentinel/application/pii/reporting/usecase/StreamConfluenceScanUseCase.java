package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanErrorClassifier;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanRunState;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKeys;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application use case orchestrating Confluence scans and PII detection.
 * What: encapsulates business/reactive flow away from the web controller.
 * Returns ScanEvent stream that the presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceScanUseCase extends AbstractStreamConfluenceScanUseCase implements
    StreamConfluenceScanPort {

    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort;

    public StreamConfluenceScanUseCase(
        ScanPipelineDependencies dependencies,
        PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort
    ) {
        super(dependencies);
        this.personallyIdentifiableInformationScanExecutionOrchestratorPort = personallyIdentifiableInformationScanExecutionOrchestratorPort;
    }

    /**
     * Streams scan events for a single Confluence space.
     * WebFlux pedagogy (technical):
     * — A scan identifier is generated to correlate all events within the stream.
     * — Mono.fromFuture(...) bridges a CompletableFuture (Confluence API) into the reactive world.
     * — flatMapMany(...) turns a Mono (0..1) into a Flux (0..N) based on the result.
     * — If the space does not exist, immediately return a single-element Flux (error event) via Flux.just(...).
     * — Otherwise, load the space pages (still via fromFuture), then delegate to runScanFlux(...)
     *   which produces a Flux<ScanResult> representing the event sequence (start, progress, results, completion...).
     * — onErrorResume captures any asynchronous error in the chain and switches to a readable business error Flux.
     * Useful reactive properties:
     * — Laziness: nothing executes until there is a subscriber on the controller side (e.g., SSE).
     * — Backpressure: the Flux emits at the rate requested by the subscriber; here, concatenation and the operators used are
     *   safe for sequential processing without memory pressure.
     */
    @Override
    public Flux<ConfluenceContentScanResult> streamSpace(String spaceKey) {
        // Unique identifier to trace and group all events of the same scan
        String scanId = UUID.randomUUID().toString();

        // Refuse the scan when a detector the operator enabled cannot be reached:
        // it would contribute no finding, making an incomplete report look clean.
        List<ConfluenceContentScanResult> preflightFailures = detectorPreflightFailures(scanId, spaceKey);
        Flux<ConfluenceContentScanResult> scanFlux = preflightFailures.isEmpty()
            ? buildSpaceScanFlux(new ScanRunState(scanId), spaceKey)
            : Flux.fromIterable(preflightFailures);

        // Start independent scan task and return subscription flux
        personallyIdentifiableInformationScanExecutionOrchestratorPort.startScan(scanId, scanFlux);
        return personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanId);
    }

    private Flux<ConfluenceContentScanResult> buildSpaceScanFlux(ScanRunState run, String spaceKey) {
        String scanId = run.scanId();
        return Mono.fromFuture(confluenceAccessor.getSpace(spaceKey))
            // Transform Mono<Optional<ConfluenceSpace>> into Flux<ScanResult>
            .flatMapMany(confluenceSpaceOpt -> {
                // Case 1: space not found → return a small Flux with a single error event
                if (confluenceSpaceOpt.isEmpty()) {
                    return Flux.just(errorEvent(scanId, spaceKey,
                        new TranslatableError(ScanErrorKeys.SPACE_NOT_FOUND, Map.of("space", spaceKey))));
                }
                // Case 2: space found → retrieve all its pages then start the scan stream
                return Mono.fromFuture(confluenceAccessor.getAllPagesInSpace(spaceKey))
                    // runScanFlux(...) already returns a Flux<ScanResult> representing the full progression
                    .flatMapMany(pages -> runScanFlux(run, spaceKey, pages, 0, pages.size()));
            })
            // Global safety net: transform any exception into a UI-consumable error event
            .onErrorResume(exception -> {
                log.error("[USECASE] Error in webflux: {}", exception.getMessage(), exception);
                run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
                persistPauseIfNeeded(run);
                return Flux.just(errorEvent(scanId, spaceKey,
                    pausedErrorOr(run, resolveErrorMessage(exception))));
            });
    }

    /**
     * Builds an error event for a failure that belongs to the run rather than to one page.
     *
     * @param error what went wrong, as the dashboard will word it for the operator
     */
    private static ConfluenceContentScanResult errorEvent(String scanId, String spaceKey,
                                                          TranslatableError error) {
        return ConfluenceContentScanResult.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .eventType(DetectionReportingEventType.ERROR.getLabel())
            .message(ScanEventFactory.describeForLogs(error))
            .errorKey(error.key())
            .errorParams(error.params())
            .emittedAt(Instant.now().toString())
            .build();
    }

    /**
     * Streams scan events for all spaces sequentially.
     * 
     * <p><strong>Business Rule:</strong> Always creates a new scan with a fresh scanId and purges
     * previous scan data. This is the behavior triggered by the "Start" button.</p>
     * 
     * <p><strong>Logic:</strong></p>
     * <ul>
     *   <li>Generates a new UUID for each fresh scan</li>
     *   <li>Purges previous scan checkpoints to ensure clean state</li>
     *   <li>For resuming a paused scan, use resumeAllSpaces(scanId) instead</li>
     * </ul>
     * 
     * WebFlux pedagogy (technical):
     * - The overall stream is split into three segments: header (MULTI_START), body (space processing), footer (MULTI_COMPLETE).
     * - Flux.concat(header, body, footer) guarantees strict sequential execution of these segments in order.
     * - Each segment is a lazy Flux; nothing starts until there is a subscriber.
     */
    @Override
    public Flux<ConfluenceContentScanResult> streamAllSpaces() {
        // Always create a new scanId for a fresh scan (Start button behavior)
        String scanCorrelationId = UUID.randomUUID().toString();
        log.info("[SCAN] Creating new scan with scanId: {}", scanCorrelationId);
        ScanRunState run = new ScanRunState(scanCorrelationId);

        // Checked BEFORE the purge below: a refused scan must leave the previous
        // results intact rather than wiping them for a run that never happens.
        List<ConfluenceContentScanResult> preflightFailures =
            detectorPreflightFailures(scanCorrelationId, null);

        // Opening segment: a single "MULTI_START" event
        Flux<ConfluenceContentScanResult> header = buildAllSpaceScanFluxHeader(scanCorrelationId);

        // Main segment: iterate over spaces and perform scans sequentially, unless an
        // enabled detector is unreachable — then the error events are the body.
        Flux<ConfluenceContentScanResult> body;
        if (preflightFailures.isEmpty()) {
            // Purge previous scan data to ensure clean state
            contentScanOrchestrator.purgePreviousScanData();
            body = buildAllSpaceScanFluxBody(run);
        } else {
            body = Flux.fromIterable(preflightFailures);
        }

        // Closing segment: a single "MULTI_COMPLETE" event, dropped for a refused scan —
        // announcing completion for a run that never opened a space would read as a clean base.
        Flux<ConfluenceContentScanResult> footer = preflightFailures.isEmpty()
            ? buildAllSpaceScanFluxFooter(run)
            : Flux.empty();

        // Sequential and ordered concatenation of segments
        Flux<ConfluenceContentScanResult> scanFlux = Flux.concat(header, body, footer);

        // Start independent scan task and return subscription flux
        personallyIdentifiableInformationScanExecutionOrchestratorPort.startScan(scanCorrelationId, scanFlux);
        return personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanCorrelationId);
    }

    @Override
    public Flux<ConfluenceContentScanResult> streamSelectedSpaces(List<String> spaceKeys) {
        // Always create a new scanId for a fresh scan
        String scanCorrelationId = UUID.randomUUID().toString();
        log.info("[SCAN] Creating new selected spaces scan with scanId: {}", scanCorrelationId);
        ScanRunState run = new ScanRunState(scanCorrelationId);

        // Checked BEFORE the purge below: a refused scan must leave the previous
        // results intact rather than wiping them for a run that never happens.
        List<ConfluenceContentScanResult> preflightFailures =
            detectorPreflightFailures(scanCorrelationId, null);

        // Opening segment: a single "MULTI_START" event
        Flux<ConfluenceContentScanResult> header = buildAllSpaceScanFluxHeader(scanCorrelationId);

        // Main segment: iterate over selected spaces and perform scans sequentially, unless
        // an enabled detector is unreachable — then the error events are the body.
        Flux<ConfluenceContentScanResult> body;
        if (preflightFailures.isEmpty()) {
            // Purge previous scan data for selected spaces to ensure clean state
            contentScanOrchestrator.purgePreviousScanDataForSpaces(spaceKeys);
            body = buildSelectedSpaceScanFluxBody(run, spaceKeys);
        } else {
            body = Flux.fromIterable(preflightFailures);
        }

        // Closing segment: a single "MULTI_COMPLETE" event, dropped for a refused scan —
        // announcing completion for a run that never opened a space would read as a clean base.
        Flux<ConfluenceContentScanResult> footer = preflightFailures.isEmpty()
            ? buildAllSpaceScanFluxFooter(run)
            : Flux.empty();

        // Sequential and ordered concatenation of segments
        Flux<ConfluenceContentScanResult> scanFlux = Flux.concat(header, body, footer);

        // Start independent scan task and return subscription flux
        personallyIdentifiableInformationScanExecutionOrchestratorPort.startScan(scanCorrelationId, scanFlux);
        return personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanCorrelationId);
    }

    private Flux<ConfluenceContentScanResult> buildSelectedSpaceScanFluxBody(ScanRunState run, List<String> spaceKeys) {
        String scanId = run.scanId();
        // Asynchronous retrieval of all spaces (Future -> Mono)
        // Optimization: We could fetch only specific spaces if the API supported it, but filtering is safe.
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            // Then unfold into Flux<ScanResult>
            .flatMapMany(allSpaces -> {
                List<ConfluenceSpace> selectedSpaces = allSpaces.stream()
                    .filter(space -> spaceKeys.contains(space.key()))
                    .toList();

                // Persist the scan scope (NOT_STARTED checkpoints) BEFORE any space is scanned,
                // so a paused scan can later be resumed within this exact scope.
                contentScanOrchestrator.initializeScanScope(scanId, resolveSpaceKeys(selectedSpaces));

                // If the list is empty, generate a small error Flux. Otherwise, create the scan Flux.
                Flux<ConfluenceContentScanResult> errorScanResultsFlux = createErrorScanResultIfNoSpace(scanId, selectedSpaces);
                return Objects.requireNonNullElseGet(errorScanResultsFlux, () -> createScanResultFlux(run, selectedSpaces));
            })
            // Global error handling: map any exception to a readable business event
            .onErrorResume(exception -> {
                log.error("[USECASE] Error in the webflux of selected spaces: {}",
                    exception.getMessage(),
                    exception);
                return Flux.just(errorEvent(scanId, null, new TranslatableError(
                    ScanErrorKeys.UNEXPECTED, Map.of("cause", resolveErrorMessage(exception)))));
            });
    }

    /**
     * Closing MULTI_COMPLETE event, suppressed when an outage paused the scan.
     *
     * <p>Announcing completion for a run that stopped early would tell the operator the
     * whole Confluence base was covered while spaces were never opened.
     */
    private Flux<ConfluenceContentScanResult> buildAllSpaceScanFluxFooter(ScanRunState run) {
        return Flux.defer(() -> {
            // Also covers the outage that struck while listing a space's pages: that space
            // never reached the per-space closing step, so this is where its pause is written.
            persistPauseIfNeeded(run);
            return run.mustPause()
                ? Flux.empty()
                : Flux.just(ConfluenceContentScanResult.builder()
                                .scanId(run.scanId())
                                .eventType(DetectionReportingEventType.MULTI_COMPLETE.getLabel())
                                .emittedAt(Instant.now().toString())
                                .build());
        });
    }

    private Flux<ConfluenceContentScanResult> buildAllSpaceScanFluxBody(ScanRunState run) {
        String scanId = run.scanId();
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            .flatMapMany(spaces -> {
                // Persist the scan scope (NOT_STARTED checkpoints) BEFORE any space is scanned,
                // so a paused scan can later be resumed within this exact scope.
                contentScanOrchestrator.initializeScanScope(scanId, resolveSpaceKeys(spaces));

                // If the list is empty, generate a small error Flux. Otherwise, create the scan Flux.
                // Note: createErrorScanResultIfNoSpace(...) returns null when everything is fine, which allows us
                // to use Objects.requireNonNullElseGet(...) to fall back to the processing Flux.
                Flux<ConfluenceContentScanResult> errrorScanResultsFlux = createErrorScanResultIfNoSpace(scanId, spaces);
                return Objects.requireNonNullElseGet(errrorScanResultsFlux, () -> createScanResultFlux(run, spaces));
            })
            // Global error handling: map any exception to a readable business event
            .onErrorResume(exception -> {
                log.error("[USECASE] Error in the multi-space webflux: {}",
                          exception.getMessage(),
                          exception);
                return Flux.just(errorEvent(scanId, null, new TranslatableError(
                    ScanErrorKeys.UNEXPECTED, Map.of("cause", resolveErrorMessage(exception)))));
            });
    }

    private Flux<ConfluenceContentScanResult> createScanResultFlux(ScanRunState run, List<ConfluenceSpace> spaces) {
        // Flux over the list of spaces to process
        return Flux.fromIterable(spaces)
            // An outage is scan-wide, so the spaces queued behind must not be opened: they
            // would fail identically, and each one started would leave a checkpoint claiming
            // it was visited. Untouched spaces keep their NOT_STARTED checkpoint and are
            // scanned in full on resume.
            .takeWhile(space -> !run.mustPause())
            // concatMap => sequential processing (important to keep a predictable order and limit memory pressure).
            // Unlike flatMap, concatMap waits for the previous stream to complete before moving to the next.
            .concatMap(
                space -> Mono.fromFuture(
                        confluenceAccessor.getAllPagesInSpace(space.key()))
                    // Then start the scan stream for this space
                    .flatMapMany(
                        pages -> runScanFlux(run,
                                             space.key(),
                                             pages, 0,
                                             pages.size()))
                    // Local error handling: map any exception to a readable business event and continue processing
                    .onErrorResume(exception -> {
                        log.error(
                            "[USECASE] Error during space scan {}: {}",
                            space.key(),
                            exception.getMessage(),
                            exception);
                        // Listing a space's pages is a Confluence call: a dropped network or VPN
                        // surfaces here, and must stop the scan instead of skipping space after space.
                        run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
                        return Flux.just(errorEvent(run.scanId(), space.key(),
                            pausedErrorOr(run, resolveErrorMessage(exception))));
                    }));
    }

    private static List<String> resolveSpaceKeys(List<ConfluenceSpace> spaces) {
        if (spaces == null) {
            return List.of();
        }
        return spaces.stream().map(ConfluenceSpace::key).toList();
    }

    private static Flux<ConfluenceContentScanResult> createErrorScanResultIfNoSpace(String scanId, List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Flux.just(errorEvent(scanId, null, TranslatableError.of(ScanErrorKeys.NO_SPACE_FOUND)));
        }
        return null;
    }

    private static Flux<ConfluenceContentScanResult> buildAllSpaceScanFluxHeader(String scanId) {
        return Flux.just(ConfluenceContentScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_START.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }
}