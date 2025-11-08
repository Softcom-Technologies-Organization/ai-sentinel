package pro.softcom.sentinelle.application.pii.reporting.usecase;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentTextExtracted;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;
import pro.softcom.sentinelle.domain.pii.scan.ScanProgress;
import pro.softcom.sentinelle.infrastructure.config.ScanTimeoutConfig;
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
    protected final ScanTimeoutConfig scanTimeoutConfig;

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
        .onErrorResume(exception -> {
            log.error("Error analyzing attachment {} for page {}: {}", 
                      extracted.attachment().name(), page.id(), exception.getMessage());
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
            .onErrorResume(
                exception -> handleDetectionError(scanId, spaceKey, page, scanProgress, exception))
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

    private Mono<ScanResult> handleDetectionError(String scanId, String spaceKey,
                                                  ConfluencePage page,
                                                  ScanProgress scanProgress,
                                                  Throwable exception) {
        log.error("Error analyzing page {}", page.id(), exception);
        double progress = scanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
        ScanResult errorEvent = scanOrchestrator.createErrorEvent(scanId, spaceKey, page.id(),
                                                              exception.getMessage(), progress);
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
}
