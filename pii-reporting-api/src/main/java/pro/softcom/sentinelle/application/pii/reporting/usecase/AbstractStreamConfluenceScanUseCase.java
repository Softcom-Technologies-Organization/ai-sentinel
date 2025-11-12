package pro.softcom.sentinelle.application.pii.reporting.usecase;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentTextExtracted;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import pro.softcom.sentinelle.domain.pii.scan.ScanProgress;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Application use case orchestrating Confluence scans and PII detection. Business intent:
 * Coordinates the scanning workflow by delegating to specialized services for event creation,
 * progress calculation, checkpoint persistence, and attachment processing. Returns a reactive
 * stream of scan events that the presentation layer can convert to SSE.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractStreamConfluenceScanUseCase {

    protected final ConfluenceAccessor confluenceAccessor;
    protected final PiiDetectorClient piiDetectorClient;
    protected final ScanOrchestrator scanOrchestrator;
    protected final AttachmentProcessor attachmentProcessor;
    protected final ScanTimeOutConfig scanTimeoutConfig;

    protected record ConfluencePageContext(String scanId, String spaceKey, String pageId,
                                           String pageTitle) {

    }

    protected Flux<ScanResult> runScanFlux(String scanId, String spaceKey,
                                           List<ConfluencePage> pages, int analyzedOffset,
                                           int originalTotal) {
        int total = pages.size();
        AtomicInteger pageIndex = new AtomicInteger(0);

        Flux<ScanResult> startEvent = createStartEvent(scanId, spaceKey, total, analyzedOffset,
                                                       originalTotal);
        Flux<ScanResult> pageEvents = buildScanResultFluxBody(scanId, spaceKey, pages,
                                                              analyzedOffset,
                                                              originalTotal, pageIndex, total);
        Flux<ScanResult> completeEvent = createCompleteEvent(scanId, spaceKey);

        return Flux.concat(startEvent, pageEvents, completeEvent)
            .doOnEach(signal -> {
                if (signal.isOnNext() && signal.get() != null) {
                    // Persist in a non-cancellable way to survive SSE disconnections
                    Mono.fromRunnable(() -> scanOrchestrator.persistEventAndCheckpoint(signal.get()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.warn("[PERSISTENCE] Failed to persist event: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .subscribe();
                }
            });
    }

    private Flux<ScanResult> createStartEvent(String scanId, String spaceKey, int total,
                                              int analyzedOffset, int originalTotal) {
        double progress = scanOrchestrator.calculateProgress(analyzedOffset, originalTotal);
        ScanResult event = scanOrchestrator.createStartEvent(scanId, spaceKey, total, progress);
        return Flux.just(event);
    }

    private Flux<ScanResult> createCompleteEvent(String scanId, String spaceKey) {
        ScanResult event = scanOrchestrator.createCompleteEvent(scanId, spaceKey);
        return Flux.just(event);
    }

    private Flux<ScanResult> buildScanResultFluxBody(String scanId, String spaceKey,
                                                     List<ConfluencePage> pages, int analyzedOffset,
                                                     int originalTotal, AtomicInteger index,
                                                     int total) {
        return Flux.fromIterable(pages)
            .publishOn(Schedulers.boundedElastic())
            .concatMap(page -> toAttachmentsMono(page.id())
                .flatMapMany(attachments -> {
                    ConfluencePageContext confluencePageContext = new ConfluencePageContext(scanId,
                                                                                            spaceKey,
                                                                                            page.id(),
                                                                                            page.title());
                    int currentIndex = index.incrementAndGet();
                    ScanProgress scanProgress = new ScanProgress(currentIndex, analyzedOffset,
                                                                originalTotal, total);
                    return processPageStream(confluencePageContext, page, attachments,
                                             scanProgress);
                })
                .onErrorResume(exception -> {
                    log.error(
                        "[ATTACHMENTS][USECASE] Erreur récupération pièces jointes page {}: {}",
                        page.id(), exception.getMessage());
                    ScanProgress scanProgress = new ScanProgress(index.get(), analyzedOffset,
                                                                originalTotal, total);
                    return processOnePage(scanId, spaceKey, page, scanProgress);
                }))
            .onErrorContinue((exception, ignoredElement) -> log.error(
                "[USECASE] Erreur lors du traitement d'une page: {}", exception.getMessage(),
                exception));
    }


    private Mono<List<AttachmentInfo>> toAttachmentsMono(String pageId) {
        var future = confluenceAccessor.getPageAttachments(pageId);
        return future != null ? Mono.fromFuture(future) : Mono.just(List.of());
    }

    private Flux<ScanResult> processPageStream(ConfluencePageContext confluencePageContext,
                                               ConfluencePage page,
                                               List<AttachmentInfo> attachments,
                                               ScanProgress scanProgress) {
        if (attachments.isEmpty()) {
            log.debug("[ATTACHMENTS][USECASE] Aucune pièce jointe pour la page {} - {}", page.id(),
                      page.title());
            return processOnePage(confluencePageContext.scanId(), confluencePageContext.spaceKey(),
                                  page, scanProgress);
        }
        attachments.forEach(attachment -> log.info(
            "[ATTACHMENTS][USECASE] pageId={} title=\"{}\" name=\"{}\" ext=\"{}\"",
            page.id(), page.title(), attachment.name(), attachment.extension()));

        return attachmentsFlux(confluencePageContext.scanId(), confluencePageContext.spaceKey(),
                               page, attachments,
                               scanProgress)
            .concatWith(
                processOnePage(confluencePageContext.scanId(), confluencePageContext.spaceKey(),
                               page, scanProgress));
    }

    private Flux<ScanResult> attachmentsFlux(String scanId, String spaceKey, ConfluencePage page,
                                             List<AttachmentInfo> attachments,
                                             ScanProgress scanProgress) {
        return attachmentProcessor.extractAttachmentsText(page.id(), attachments)
            .flatMap(extracted -> analyzeAttachmentText(scanId, spaceKey, page, extracted,
                                                       scanProgress));
    }

    private Mono<ScanResult> analyzeAttachmentText(String scanId, String spaceKey,
                                                   ConfluencePage page,
                                                   AttachmentTextExtracted extracted,
                                                   ScanProgress scanProgress) {
        return Mono.fromCallable(() -> {
            ContentPiiDetection detection = detectPii(extracted.extractedText());
            double progress = scanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
                scanProgress.originalTotal());

            return scanOrchestrator.createAttachmentItemEvent(
                scanId, spaceKey, page, extracted.attachment(), extracted.extractedText(), detection,
                progress);
        })
        .timeout(scanTimeoutConfig.getPiiDetection())
        .onErrorResume(TimeoutException.class, exception -> {
            log.warn("[TIMEOUT][REACTOR] Space={}, PageId={}, AttachmentName=\"{}\", ReactorTimeout exceeded",
                    spaceKey, page.id(), extracted.attachment().name());
            
            double progress = scanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
                scanProgress.originalTotal());
            
            return Mono.just(scanOrchestrator.createErrorEvent(
                scanId, spaceKey, page.id(),
                "PII detection timeout (Reactor) for attachment: " + extracted.attachment().name(),
                progress));
        })
        .onErrorResume(exception -> {
            // Try to find StatusRuntimeException in the cause chain
            StatusRuntimeException grpcException = findGrpcException(exception);
            
            if (grpcException != null) {
                return handleGrpcError(scanId, spaceKey, page, extracted.attachment(), scanProgress, grpcException);
            }
            
            // Fallback: general error handling
            log.error("[ERROR][GENERAL] Space={}, PageId={}, AttachmentName=\"{}\", Error analyzing attachment",
                      spaceKey, page.id(), extracted.attachment().name(), exception);
            
            double progress = scanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
                scanProgress.originalTotal());
            
            return Mono.just(scanOrchestrator.createErrorEvent(
                scanId, spaceKey, page.id(), 
                "Error analyzing attachment: " + exception.getMessage(), 
                progress));
        });
    }


    private Flux<ScanResult> processOnePage(String scanId, String spaceKey, ConfluencePage page,
                                            ScanProgress scanProgress) {
        String content = extractPageContent(page);

        double startProgress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
            scanProgress.originalTotal());
        ScanResult pageStart = scanOrchestrator.createPageStartEvent(scanId, spaceKey, page,
                                                                 scanProgress.currentIndex(),
                                                                 scanProgress.originalTotal(), startProgress);

        Flux<ScanResult> itemEvent = createPageItemEvent(scanId, spaceKey, page, content, scanProgress);

        double completeProgress = scanOrchestrator.calculateProgress(scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                                                                       scanProgress.originalTotal());
        ScanResult pageComplete = scanOrchestrator.createPageCompleteEvent(scanId, spaceKey, page,
                                                                       completeProgress);

        return Flux.just(pageStart)
            .concatWith(itemEvent)
            .concatWith(Mono.just(pageComplete))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ScanResult> createPageItemEvent(String scanId, String spaceKey,
                                                 ConfluencePage page,
                                                 String content, ScanProgress scanProgress) {
        if (isBlank(content)) {
            return createEmptyPageItem(scanId, spaceKey, page, scanProgress);
        }

        return Mono.fromCallable(() -> detectPii(content))
            .timeout(scanTimeoutConfig.getPiiDetection())
            .map(detection -> buildPageItemEvent(scanId, page, content, detection, scanProgress))
            .onErrorResume(exception -> {
                // Handle TimeoutException directly
                if (exception instanceof TimeoutException) {
                    return handleReactorTimeoutError(scanId, spaceKey, page, scanProgress);
                }
                
                // Try to find StatusRuntimeException in the cause chain
                StatusRuntimeException grpcException = findGrpcException(exception);
                
                if (grpcException != null) {
                    return handleGrpcError(scanId, spaceKey, page, null, scanProgress, grpcException);
                }
                
                // Fallback: general error handling
                return handleDetectionError(scanId, spaceKey, page, scanProgress, exception);
            })
            .flux();
    }

    private Flux<ScanResult> createEmptyPageItem(String scanId, String spaceKey,
                                                 ConfluencePage page,
                                                 ScanProgress scanProgress) {
        double progress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
        ScanResult event = scanOrchestrator.createEmptyPageItemEvent(scanId, spaceKey, page, progress);
        return Flux.just(event);
    }

    private ScanResult buildPageItemEvent(String scanId, ConfluencePage page,
                                          String content, ContentPiiDetection detection,
                                          ScanProgress scanProgress) {
        double progress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
        return scanOrchestrator.createPageItemEvent(scanId, page.spaceKey(), page, content, detection,
                                                progress);
    }

    private Mono<ScanResult> handleReactorTimeoutError(String scanId, String spaceKey,
                                                       ConfluencePage page,
                                                       ScanProgress scanProgress) {
        log.warn("[TIMEOUT][REACTOR] Space={}, PageId={}, PageTitle=\"{}\", ReactorTimeout exceeded",
                spaceKey, page.id(), page.title());
        
        double progress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
        
        ScanResult errorEvent = scanOrchestrator.createErrorEvent(
            scanId, spaceKey, page.id(),
            "PII detection timeout (Reactor) for page: " + page.title(),
            progress);
        
        return Mono.just(errorEvent);
    }

    private Mono<ScanResult> handleGrpcError(String scanId, String spaceKey,
                                            ConfluencePage page,
                                            AttachmentInfo attachment,
                                            ScanProgress scanProgress,
                                            StatusRuntimeException exception) {
        String targetType = attachment != null ? "Attachment" : "Page";
        String targetName = attachment != null ? attachment.name() : page.title();
        String targetIdentifier = attachment != null ? page.id() + "/" + attachment.name() : page.id();
        
        if (exception.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            log.warn("[TIMEOUT][GRPC_DEADLINE_EXCEEDED] Space={}, {}=\"{}\", Identifier={}, gRPC deadline exceeded",
                    spaceKey, targetType, targetName, targetIdentifier);
            
            double progress = scanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                scanProgress.originalTotal());
            
            String errorMessage = attachment != null
                ? "PII detection timeout (gRPC DEADLINE_EXCEEDED) for attachment: " + targetName
                : "PII detection timeout (gRPC DEADLINE_EXCEEDED) for page: " + targetName;
            
            ScanResult errorEvent = scanOrchestrator.createErrorEvent(
                scanId, spaceKey, page.id(), errorMessage, progress);
            
            return Mono.just(errorEvent);
        } else {
            log.error("[ERROR][GRPC] Space={}, {}=\"{}\", Identifier={}, gRPC error: {} - {}",
                    spaceKey, targetType, targetName, targetIdentifier,
                    exception.getStatus().getCode(), exception.getMessage());
            
            double progress = scanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                scanProgress.originalTotal());
            
            String errorMessage = "PII detection failed (gRPC " + exception.getStatus().getCode() + ")";
            ScanResult errorEvent = scanOrchestrator.createErrorEvent(
                scanId, spaceKey, page.id(), errorMessage, progress);
            
            return Mono.just(errorEvent);
        }
    }

    private Mono<ScanResult> handleDetectionError(String scanId, String spaceKey,
                                                  ConfluencePage page,
                                                  ScanProgress scanProgress,
                                                  Throwable exception) {
        log.error("[ERROR][GENERAL] Space={}, PageId={}, PageTitle=\"{}\", Error analyzing page",
                 spaceKey, page.id(), page.title(), exception);
        
        double progress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
        
        ScanResult errorEvent = scanOrchestrator.createErrorEvent(
            scanId, spaceKey, page.id(),
            "Error analyzing page: " + exception.getMessage(),
            progress);
        
        return Mono.just(errorEvent);
    }

    private String extractPageContent(ConfluencePage page) {
        return page.content() != null ? page.content().body() : "";
    }

    private ContentPiiDetection detectPii(String content) {
        String safeContent = content != null ? content : "";
        int charCount = safeContent.length();
        long startTime = System.currentTimeMillis();
        ContentPiiDetection contentPiiDetection = piiDetectorClient.analyzeContent(safeContent);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        Mono.fromRunnable(() -> {
            log.info("Content: {}", safeContent);
            log.info("Time to send and received content pii scan result: {}", duration);
            log.info("Pii content: {}", contentPiiDetection);
            double charsPerSecond = duration > 0 ? (charCount * 1000.0) / duration : 0;
            log.info("[PERFORMANCE] Scan throughput: {} chars/sec ({} chars scanned in {} ms)", 
                    String.format(Locale.ROOT, "%.2f", charsPerSecond), charCount, duration);
        })
        .subscribeOn(Schedulers.parallel())
        .subscribe();
        
        return contentPiiDetection;
    }

    boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Searches through the exception cause chain to find a StatusRuntimeException.
     * This is necessary because gRPC exceptions are often wrapped in other exception types
     * like PiiDetectionException.
     *
     * @param throwable The exception to search through
     * @return StatusRuntimeException if found in the cause chain, null otherwise
     */
    private StatusRuntimeException findGrpcException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        
        // Check if the throwable itself is a StatusRuntimeException
        if (throwable instanceof StatusRuntimeException sre) {
            return sre;
        }
        
        // Search through the cause chain
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current instanceof StatusRuntimeException sre) {
                return sre;
            }
        }
        
        return null;
    }
}
