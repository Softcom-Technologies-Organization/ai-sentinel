package pro.softcom.aisentinel.application.pii.reporting.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application use case orchestrating Confluence scans and PII detection.
 * What: encapsulates business/reactive flow away from the web controller.
 * Returns ScanEvent stream that the presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceScanUseCase extends AbstractStreamConfluenceScanUseCase implements
    StreamConfluenceScanPort {

    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort;
    private final ScanCheckpointRepository scanCheckpointRepository;

    public StreamConfluenceScanUseCase(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ContentScanOrchestrator contentScanOrchestrator,
        AttachmentProcessor attachmentProcessor,
        ScanTimeOutConfig scanTimeoutConfig,
        HtmlContentParser htmlContentParser,
        PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort,
        ScanCheckpointRepository scanCheckpointRepository) {
        super(confluenceAccessor, piiDetectorClient, contentScanOrchestrator, attachmentProcessor, scanTimeoutConfig, htmlContentParser);
        this.personallyIdentifiableInformationScanExecutionOrchestratorPort = personallyIdentifiableInformationScanExecutionOrchestratorPort;
        this.scanCheckpointRepository = scanCheckpointRepository;
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

        // Build the scan flux
        Flux<ConfluenceContentScanResult> scanFlux = Mono.fromFuture(confluenceAccessor.getSpace(spaceKey))
            // Transform Mono<Optional<ConfluenceSpace>> into Flux<ScanResult>
            .flatMapMany(confluenceSpaceOpt -> {
                // Case 1: space not found → return a small Flux with a single error event
                if (confluenceSpaceOpt.isEmpty()) {
                    return Flux.just(ConfluenceContentScanResult.builder()
                                         .scanId(scanId)
                                         .spaceKey(spaceKey)
                                         .eventType(DetectionReportingEventType.ERROR.getLabel())
                                         .message("Espace non trouvé")
                                         .emittedAt(Instant.now().toString())
                                         .build());
                }
                // Case 2: space found → retrieve all its pages then start the scan stream
                return Mono.fromFuture(confluenceAccessor.getAllPagesInSpace(spaceKey))
                    // runScanFlux(...) already returns a Flux<ScanResult> representing the full progression
                    .flatMapMany(pages -> runScanFlux(scanId, spaceKey, pages, 0, pages.size()));
            })
            // Global safety net: transform any exception into a UI-consumable error event
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur dans le flux: {}", exception.getMessage(), exception);
                return Flux.just(ConfluenceContentScanResult.builder()
                                     .scanId(scanId)
                                     .spaceKey(spaceKey)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });

        // Start independent scan task and return subscription flux
        personallyIdentifiableInformationScanExecutionOrchestratorPort.startScan(scanId, scanFlux);
        return personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanId);
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
        
        // Purge previous scan data to ensure clean state
        purgePreviousScanData();

        // Opening segment: a single "MULTI_START" event
        Flux<ConfluenceContentScanResult> header = buildAllSpaceScanFluxHeader(scanCorrelationId);

        // Main segment: iterate over spaces and perform scans sequentially
        Flux<ConfluenceContentScanResult> body = buildAllSpaceScanFluxBody(scanCorrelationId);

        // Closing segment: a single "MULTI_COMPLETE" event
        Flux<ConfluenceContentScanResult> footer = buildAllSpaceScanFluxFooter(scanCorrelationId);

        // Sequential and ordered concatenation of segments
        Flux<ConfluenceContentScanResult> scanFlux = Flux.concat(header, body, footer);

        // Start independent scan task and return subscription flux
        personallyIdentifiableInformationScanExecutionOrchestratorPort.startScan(scanCorrelationId, scanFlux);
        return personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanCorrelationId);
    }

    private static Flux<ConfluenceContentScanResult> buildAllSpaceScanFluxFooter(String scanId) {
        return Flux.just(ConfluenceContentScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_COMPLETE.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }

    private Flux<ConfluenceContentScanResult> buildAllSpaceScanFluxBody(String scanId) {
        // Asynchronous retrieval of all spaces (Future -> Mono)
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            // Then unfold into Flux<ScanResult>
            .flatMapMany(spaces -> {
                // If the list is empty, generate a small error Flux. Otherwise, create the scan Flux.
                // Note: createErrorScanResultIfNoSpace(...) returns null when everything is fine, which allows us
                // to use Objects.requireNonNullElseGet(...) to fall back to the processing Flux.
                Flux<ConfluenceContentScanResult> errrorScanResultsFlux = createErrorScanResultIfNoSpace(scanId, spaces);
                return Objects.requireNonNullElseGet(errrorScanResultsFlux, () -> createScanResultFlux(scanId, spaces));
            })
            // Global error handling: map any exception to a readable business event
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur globale du flux multi-espaces: {}",
                          exception.getMessage(),
                          exception);
                return Flux.just(ConfluenceContentScanResult.builder()
                                     .scanId(scanId)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });
    }

    private Flux<ConfluenceContentScanResult> createScanResultFlux(String scanId, List<ConfluenceSpace> spaces) {
        // Flux over the list of spaces to process
        return Flux.fromIterable(spaces)
            // concatMap => sequential processing (important to keep a predictable order and limit memory pressure).
            // Unlike flatMap, concatMap waits for the previous stream to complete before moving to the next.
            .concatMap(
                space -> Mono.fromFuture(
                        confluenceAccessor.getAllPagesInSpace(space.key()))
                    // Then start the scan stream for this space
                    .flatMapMany(
                        pages -> runScanFlux(scanId,
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
                        return Flux.just(
                            ConfluenceContentScanResult.builder()
                                .scanId(scanId)
                                .spaceKey(space.key())
                                .eventType(DetectionReportingEventType.ERROR.getLabel())
                                .message(exception.getMessage())
                                .emittedAt(Instant.now().toString())
                                .build());
                    }));
    }

    private static Flux<ConfluenceContentScanResult> createErrorScanResultIfNoSpace(String scanId, List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Flux.just(ConfluenceContentScanResult.builder()
                                 .scanId(scanId)
                                 .eventType(DetectionReportingEventType.ERROR.getLabel())
                                 .message("Aucun espace trouvé")
                                 .emittedAt(Instant.now().toString())
                                 .build());
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

    /**
     * Purges previous scan data to ensure a clean state before starting a new scan.
     */
    private void purgePreviousScanData() {
        try {
            log.info("[SCAN] Purging previous active scan data before starting new scan");
            scanCheckpointRepository.deleteActiveScanCheckpoints();
            log.info("[SCAN] Previous scan data purged successfully");
        } catch (Exception e) {
            log.error("[SCAN] Failed to purge previous scan data: {}", e.getMessage(), e);
            // Don't fail the scan if purge fails - log and continue
        }
    }

}
