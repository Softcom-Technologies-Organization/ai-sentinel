package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanRemainingPages;
import pro.softcom.aisentinel.domain.pii.reporting.ScanRemainingPagesCalculator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;

/**
 * Application use case orchestrating Confluence scans and PII detection. What: encapsulates
 * business/reactive flow away from the web controller. Returns ScanEvent stream that the
 * presentation layer can turn into SSE.
 */
@Slf4j
public class StreamConfluenceResumeScanUseCase extends
    AbstractStreamConfluenceScanUseCase implements StreamConfluenceResumeScanPort {

    private final ScanCheckpointRepository scanCheckpointRepository;

    public StreamConfluenceResumeScanUseCase(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ContentScanOrchestrator contentScanOrchestrator,
        AttachmentProcessor attachmentProcessor,
        ScanCheckpointRepository scanCheckpointRepository,
        ScanTimeOutConfig scanTimeoutConfig,
        HtmlContentParser htmlContentParser) {
        super(confluenceAccessor, piiDetectorClient, contentScanOrchestrator, attachmentProcessor, scanTimeoutConfig, htmlContentParser);
        this.scanCheckpointRepository = scanCheckpointRepository;
    }


    @Override
    public Flux<ConfluenceContentScanResult> resumeAllSpaces(String scanId) {
        if (isBlank(scanId)) {
            return Flux.empty();
        }
        return Mono.fromFuture(confluenceAccessor.getAllSpaces())
            .flatMapMany(spaces ->
                             Flux.fromIterable(spaces)
                                 .concatMap(space -> resumeScanResultFlux(scanId, space)))
            .onErrorResume(exception -> {
                log.error("[USECASE] Error when resuming scan: {}", exception.getMessage(),
                          exception);
                return buildErrorScanResultFlux(scanId, null, exception);
            });
    }

    private Flux<ConfluenceContentScanResult> resumeScanResultFlux(String scanId, ConfluenceSpace space) {
        try {
            var scanCheckpoint = scanCheckpointRepository.findByScanAndSpace(scanId, space.key())
                .orElse(null);
            Flux<ConfluenceContentScanResult> empty = checkScanCompletionAndGenerateFlux(scanCheckpoint);
            return Objects.requireNonNullElseGet(empty, () -> Mono.fromFuture(
                    confluenceAccessor.getAllPagesInSpace(space.key()))
                .flatMapMany(pages -> {
                    ScanRemainingPages scanRemainingPages =
                        ScanRemainingPagesCalculator.computeRemainPages(pages,
                                                                        scanCheckpoint);
                    if (scanRemainingPages.remaining().isEmpty()) {
                        return Flux.empty();
                    }
                    return runScanFlux(scanId, space.key(), scanRemainingPages.remaining(),
                                       scanRemainingPages.analyzedOffset(),
                                       scanRemainingPages.originalTotal());
                })
                .onErrorResume(exception -> {
                    log.error("[USECASE] Error when resuming scan of space {}: {}",
                              space.key(), exception.getMessage(), exception);
                    return buildErrorScanResultFlux(scanId, space, exception);
                }));
        } catch (Exception exception) {
            log.error("[USECASE] Error when resuming scan of space {}: {}",
                      space.key(), exception.getMessage(), exception);
            return buildErrorScanResultFlux(scanId, space, exception);
        }
    }

    private static Flux<ConfluenceContentScanResult> checkScanCompletionAndGenerateFlux(
        ScanCheckpoint checkpointOptional) {
        if (checkpointOptional != null && checkpointOptional.scanStatus() == ScanStatus.COMPLETED) {
            return Flux.empty();
        }
        return null;
    }

    private static Flux<ConfluenceContentScanResult> buildErrorScanResultFlux(String scanId, ConfluenceSpace space,
                                                                              Throwable exception) {
        return Flux.just(ConfluenceContentScanResult.builder()
                             .scanId(scanId)
                             .spaceKey(space != null ? space.key() : null)
                             .eventType(DetectionReportingEventType.ERROR.getLabel())
                             .message(exception.getMessage())
                             .emittedAt(Instant.now().toString())
                             .build());
    }
}
