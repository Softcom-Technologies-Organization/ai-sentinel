package pro.softcom.sentinelle.application.pii.reporting.usecase;

import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.domain.pii.reporting.ScanRemainingPages;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResumeRemainingPagesCalculator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application use case orchestrating Confluence scans and PII detection. What: encapsulates
 * business/reactive flow away from the web controller. Returns ScanEvent stream that the
 * presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceResumeScanUseCaseImpl extends
    AbstractStreamConfluenceScanUseCase implements StreamConfluenceResumeScanUseCase {

    private final ScanCheckpointRepository scanCheckpointRepository;

    public StreamConfluenceResumeScanUseCaseImpl(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ScanOrchestrator scanOrchestrator,
        AttachmentProcessor attachmentProcessor,
        ScanCheckpointRepository scanCheckpointRepository,
        ScanTimeOutConfig scanTimeoutConfig) {
        super(confluenceAccessor, piiDetectorClient, scanOrchestrator, attachmentProcessor, scanTimeoutConfig);
        this.scanCheckpointRepository = scanCheckpointRepository;
    }


    @Override
    public Flux<ScanResult> resumeAllSpaces(String scanId) {
        if (isBlank(scanId)) {
            return Flux.empty();
        }
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            .flatMapMany(spaces ->
                             Flux.fromIterable(spaces)
                                 .concatMap(space -> resumeScanResultFlux(scanId, space)))
            .onErrorResume(exception -> {
                log.error("[USECASE] Erreur globale de reprise: {}", exception.getMessage(),
                          exception);
                return buildErrorScanResultFlux(scanId, null, exception);
            });
    }

    private Flux<ScanResult> resumeScanResultFlux(String scanId, ConfluenceSpace space) {
        try {
            var scanCheckpoint = scanCheckpointRepository.findByScanAndSpace(scanId, space.key())
                .orElse(null);
            Flux<ScanResult> empty = checkScanCompletionAndGenerateFlux(scanCheckpoint);
            return Objects.requireNonNullElseGet(empty, () -> Mono.fromFuture(
                    confluenceAccessor.getAllPagesInSpace(space.key()))
                .flatMapMany(pages -> {
                    ScanRemainingPages scanRemainingPages =
                        ScanResumeRemainingPagesCalculator.computeRemainPages(pages,
                                                                              scanCheckpoint);
                    if (scanRemainingPages.remaining().isEmpty()) {
                        return Flux.empty();
                    }
                    return runScanFlux(scanId, space.key(), scanRemainingPages.remaining(),
                                       scanRemainingPages.analyzedOffset(),
                                       scanRemainingPages.originalTotal());
                })
                .onErrorResume(exception -> {
                    log.error("[USECASE] Erreur lors de la reprise de l'espace {}: {}",
                              space.key(), exception.getMessage(), exception);
                    return buildErrorScanResultFlux(scanId, space, exception);
                }));
        } catch (Exception exception) {
            log.error("[USECASE] Erreur lors de la reprise (préparation) de l'espace {}: {}",
                      space.key(), exception.getMessage(), exception);
            return buildErrorScanResultFlux(scanId, space, exception);
        }
    }

    private static Flux<ScanResult> checkScanCompletionAndGenerateFlux(
        ScanCheckpoint checkpointOptional) {
        if (checkpointOptional != null && checkpointOptional.scanStatus() == ScanStatus.COMPLETED) {
            return Flux.empty();
        }
        return null;
    }

    private static Flux<ScanResult> buildErrorScanResultFlux(String scanId, ConfluenceSpace space,
                                                             Throwable exception) {
        return Flux.just(ScanResult.builder()
                             .scanId(scanId)
                             .spaceKey(space != null ? space.key() : null)
                             .eventType(DetectionReportingEventType.ERROR.getLabel())
                             .message(exception.getMessage())
                             .emittedAt(Instant.now().toString())
                             .build());
    }
}
