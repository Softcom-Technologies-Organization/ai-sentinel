package pro.softcom.sentinelle.application.pii.reporting.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application use case orchestrating Confluence scans and PII detection.
 * What: encapsulates business/reactive flow away from the web controller.
 * Returns ScanEvent stream that the presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceScanUseCaseImpl extends AbstractStreamConfluenceScanUseCase implements StreamConfluenceScanUseCase {


    public StreamConfluenceScanUseCaseImpl(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ScanOrchestrator scanOrchestrator,
        AttachmentProcessor attachmentProcessor) {
        super(confluenceAccessor, piiDetectorClient, scanOrchestrator, attachmentProcessor);
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
    public Flux<ScanResult> streamSpace(String spaceKey) {
        // Unique identifier to trace and group all events of the same scan
        String scanId = UUID.randomUUID().toString();

        // Bridging from Future to reactive. The request is not executed until there is a subscriber.
        return Mono.fromFuture(confluenceAccessor.getSpace(spaceKey))
            // Transform Mono<Optional<ConfluenceSpace>> into Flux<ScanResult>
            .flatMapMany(confluenceSpaceOpt -> {
                // Case 1: space not found → return a small Flux with a single error event
                if (confluenceSpaceOpt.isEmpty()) {
                    return Flux.just(ScanResult.builder()
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
                return Flux.just(ScanResult.builder()
                                     .scanId(scanId)
                                     .spaceKey(spaceKey)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });
    }

    /**
     * Streams scan events for all spaces sequentially.
     * WebFlux pedagogy (technical):
     * - The overall stream is split into three segments: header (MULTI_START), body (space processing), footer (MULTI_COMPLETE).
     * - Flux.concat(header, body, footer) guarantees strict sequential execution of these segments in order.
     * - Each segment is a lazy Flux; nothing starts until there is a subscriber.
     */
    @Override
    public Flux<ScanResult> streamAllSpaces() {
        String scanCorrelationId = UUID.randomUUID().toString();

        // Opening segment: a single "MULTI_START" event
        Flux<ScanResult> header = buildAllSpaceScanFluxHeader(scanCorrelationId);

        // Main segment: iterate over spaces and perform scans sequentially
        Flux<ScanResult> body = buildAllSpaceScanFluxBody(scanCorrelationId);

        // Closing segment: a single "MULTI_COMPLETE" event
        Flux<ScanResult> footer = buildAllSpaceScanFluxFooter(scanCorrelationId);

        // Sequential and ordered concatenation of segments
        return Flux.concat(header, body, footer);
    }

    private static Flux<ScanResult> buildAllSpaceScanFluxFooter(String scanId) {
        return Flux.just(ScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_COMPLETE.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }

    private Flux<ScanResult> buildAllSpaceScanFluxBody(String scanId) {
        // Asynchronous retrieval of all spaces (Future -> Mono)
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            // Then unfold into Flux<ScanResult>
            .flatMapMany(spaces -> {
                // If the list is empty, generate a small error Flux. Otherwise, create the scan Flux.
                // Note: createErrorScanResultIfNoSpace(...) returns null when everything is fine, which allows us
                // to use Objects.requireNonNullElseGet(...) to fall back to the processing Flux.
                Flux<ScanResult> errrorScanResultsFlux = createErrorScanResultIfNoSpace(scanId, spaces);
                return Objects.requireNonNullElseGet(errrorScanResultsFlux, () -> createScanResultFlux(scanId, spaces));
            })
            // Global error handling: map any exception to a readable business event
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur globale du flux multi-espaces: {}",
                          exception.getMessage(),
                          exception);
                return Flux.just(ScanResult.builder()
                                     .scanId(scanId)
                                     .eventType(DetectionReportingEventType.ERROR.getLabel())
                                     .message(exception.getMessage())
                                     .emittedAt(Instant.now().toString())
                                     .build());
            });
    }

    private Flux<ScanResult> createScanResultFlux(String scanId, List<ConfluenceSpace> spaces) {
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
                    // Filet d'erreur local à un espace: on émet un évènement d'erreur mais on continue les autres espaces
                    .onErrorResume(exception -> {
                        log.error(
                            "[USECASE] Erreur lors du scan de l'espace {}: {}",
                            space.key(),
                            exception.getMessage(),
                            exception);
                        return Flux.just(
                            ScanResult.builder()
                                .scanId(scanId)
                                .spaceKey(space.key())
                                .eventType(DetectionReportingEventType.ERROR.getLabel())
                                .message(exception.getMessage())
                                .emittedAt(Instant.now().toString())
                                .build());
                    }));
    }

    private static Flux<ScanResult> createErrorScanResultIfNoSpace(String scanId, List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Flux.just(ScanResult.builder()
                                 .scanId(scanId)
                                 .eventType(DetectionReportingEventType.ERROR.getLabel())
                                 .message("Aucun espace trouvé")
                                 .emittedAt(Instant.now().toString())
                                 .build());
        }
        return null;
    }

    private static Flux<ScanResult> buildAllSpaceScanFluxHeader(String scanId) {
        return Flux.just(ScanResult.builder()
                             .scanId(scanId)
                             .eventType(DetectionReportingEventType.MULTI_START.getLabel())
                             .emittedAt(Instant.now().toString())
                             .build());
    }

}
