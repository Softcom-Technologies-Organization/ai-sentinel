package pro.softcom.aisentinel.application.pii.reporting.usecase;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentTextExtracted;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanTimeFalsePositiveSuppressor;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanErrorClassifier;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanRunState;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanSpaceStatsCollector;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKeys;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKind;
import pro.softcom.aisentinel.domain.pii.scan.ScanProgress;
import pro.softcom.aisentinel.domain.pii.scan.TranslatableError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application use case orchestrating Confluence scans and PII detection. Business intent:
 * Coordinates the scanning workflow by delegating to specialized services for event creation,
 * progress calculation, checkpoint persistence, and attachment processing. Returns a reactive
 * stream of scan events that the presentation layer can convert to SSE.
 */
@Slf4j
public abstract class AbstractStreamConfluenceScanUseCase {

    private static final String CAUSE_PARAM = "cause";

    protected final ConfluenceAccessor confluenceAccessor;
    protected final PiiDetectorClient piiDetectorClient;
    protected final ContentScanOrchestrator contentScanOrchestrator;
    protected final AttachmentProcessor attachmentProcessor;
    protected final ScanTimeOutConfig scanTimeoutConfig;
    protected final HtmlContentParser htmlContentParser;
    protected final ScanSpaceStatsCollector scanSpaceStatsCollector;

    /** Drops detections already flagged false positive before persistence, statistics and the
     *  live view; {@code null} disables scan-time suppression. */
    protected final ScanTimeFalsePositiveSuppressor falsePositiveSuppressor;

    /** Number of pages in flight at once (attachment retrieval, extraction and detection), >= 1. */
    protected final int pageConcurrency;

    /** Live count of in-flight {@code detectPii} calls, surfaced in throughput logs
     *  so operators can confirm the configured page concurrency is effective. */
    private final AtomicInteger inflightDetections = new AtomicInteger(0);

    protected AbstractStreamConfluenceScanUseCase(ScanPipelineDependencies dependencies) {
        this.confluenceAccessor = dependencies.confluenceAccessor();
        this.piiDetectorClient = dependencies.piiDetectorClient();
        this.contentScanOrchestrator = dependencies.contentScanOrchestrator();
        this.attachmentProcessor = dependencies.attachmentProcessor();
        this.scanTimeoutConfig = dependencies.scanTimeoutConfig();
        this.htmlContentParser = dependencies.htmlContentParser();
        this.scanSpaceStatsCollector = dependencies.scanSpaceStatsCollector();
        this.falsePositiveSuppressor = dependencies.falsePositiveSuppressor();
        this.pageConcurrency = Math.max(1, dependencies.pageConcurrency());
    }

    protected record ConfluencePageContext(String scanId, String spaceKey, String pageId,
                                           String pageTitle) {

    }

    /**
     * Per-page view of the run: the run-wide pause verdict, plus whether this very
     * page hit the outage.
     *
     * <p>Both are needed and neither replaces the other. The run-wide flag stops
     * feeding new pages; the page-local one decides whether this page's
     * {@code pageComplete} may be emitted. Keying that decision on the run-wide flag
     * instead would drop the {@code pageComplete} of pages that were concurrently
     * analysed just fine, and a resume would then scan them a second time —
     * double-counting their severities.
     */
    private record PageScanState(ScanRunState run, AtomicBoolean pageOutage) {

        PageScanState(ScanRunState run) {
            this(run, new AtomicBoolean(false));
        }

        String scanId() {
            return run.scanId();
        }

        /** Records an error, pausing the run and holding this page back when the kind warrants it. */
        void reportError(ScanErrorKind kind, String cause) {
            if (kind.pausesScan()) {
                pageOutage.set(true);
            }
            run.requestPause(kind, cause);
        }

        boolean hitOutage() {
            return pageOutage.get();
        }
    }

    protected Flux<ConfluenceContentScanResult> runScanFlux(ScanRunState run, String spaceKey,
                                                            List<ConfluencePage> pages, int analyzedOffset,
                                                            int originalTotal) {
        String scanId = run.scanId();
        int total = pages.size();

        Flux<ConfluenceContentScanResult> startEvent = createStartEvent(scanId, spaceKey, total, analyzedOffset,
                                                                        originalTotal);
        Flux<ConfluenceContentScanResult> pageEvents = buildScanResultFluxBody(run, spaceKey, pages,
                                                                               analyzedOffset,
                                                                               originalTotal, total);
        // Deferred: whether this space completed or was cut short by an outage is only
        // known once every page above has been processed.
        Flux<ConfluenceContentScanResult> completeEvent = Flux.defer(() -> closeSpaceScan(run, spaceKey));

        // Load the space's false-positive finding ids once and drop matching detections from every
        // event before any consumer sees it, so scan events, severity counters, per-space stats and
        // the live SSE view stay consistent. Suppression is a pure in-memory transform here; the set
        // is empty (no-op) when suppression is disabled or the space has no false positive.
        Set<String> falsePositiveIds = falsePositiveSuppressor == null
            ? Set.of()
            : falsePositiveSuppressor.falsePositiveIds(spaceKey);

        return Flux.concat(startEvent, pageEvents, completeEvent)
            .map(event -> suppressFalsePositives(event, falsePositiveIds))
            .doOnEach(signal -> {
                if (signal.isOnNext() && signal.get() != null) {
                    ConfluenceContentScanResult event = signal.get();
                    
                    // CRITICAL: Persist checkpoint SYNCHRONOUSLY to avoid race conditions
                    // When user refreshes the page, the resume scan must read the latest checkpoint.
                    // If checkpoint persistence were async, stale data could cause pages to be re-scanned,
                    // leading to duplicated severity counts (bug fix for severity counts doubled on refresh).
                    contentScanOrchestrator.persistCheckpointSynchronously(event);
                    
                    // Async operations (severity counts, event store) can safely continue in background
                    // These are additive operations that won't cause issues if the SSE disconnects
                    Mono.fromRunnable(() -> contentScanOrchestrator.persistEventAsyncOperations(event))
                        .subscribeOn(Schedulers.boundedElastic())
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                        .onErrorResume(e -> {
                            log.warn("[PERSISTENCE] Failed to persist async operations: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .subscribe();

                    // Per-space scan statistics: additive, atomic at the SQL level, and
                    // self-contained (errors swallowed inside the collector) so a stats
                    // failure can never make the scan itself fail.
                    Mono.fromRunnable(() -> scanSpaceStatsCollector.recordEvent(event))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.warn("[SPACE_STATS] Failed to record scan space stats: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .subscribe();
                }
            });
    }

    private Flux<ConfluenceContentScanResult> createStartEvent(String scanId, String spaceKey, int total,
                                                               int analyzedOffset, int originalTotal) {
        double progress = contentScanOrchestrator.calculateProgress(analyzedOffset, originalTotal);
        ConfluenceContentScanResult event = contentScanOrchestrator.createStartEvent(scanId, spaceKey, total, progress);
        return Flux.just(event);
    }

    /**
     * Closes a space: either it completed, or an outage paused the scan.
     *
     * <p><strong>Business rule:</strong> a space cut short by an outage must NOT emit
     * its {@code complete} event. That event marks the space COMPLETED, and a
     * COMPLETED space is skipped by the resume path — the pages never analysed would
     * be presented as clean forever. Instead the scan is paused and the cause is
     * streamed, so the Resume button restarts exactly where the outage struck.
     */
    private Flux<ConfluenceContentScanResult> closeSpaceScan(ScanRunState run, String spaceKey) {
        Optional<TranslatableError> pauseError = run.pauseError();
        if (pauseError.isEmpty()) {
            return Flux.just(contentScanOrchestrator.createCompleteEvent(run.scanId(), spaceKey));
        }

        // Paused before the event is emitted, so the dashboard's next status poll already
        // reads PAUSED and offers Resume rather than a scan that looks stalled.
        persistPauseIfNeeded(run);
        log.error("[SCAN] Scan {} paused on space {}: {}", run.scanId(), spaceKey,
                  ScanEventFactory.describeForLogs(pauseError.get()));
        return Flux.just(contentScanOrchestrator.createErrorEvent(run.scanId(), spaceKey, null,
                                                                  pauseError.get(), 0.0));
    }

    /**
     * Writes the PAUSED status for a run stopped by an outage, at most once.
     *
     * <p>Callable from every path that can detect the outage — analysing a page, or
     * listing the pages of a space that never started — because whichever spots it
     * first must be the one that makes the scan resumable.
     *
     * @param run the run to pause; a healthy run is left untouched
     */
    protected void persistPauseIfNeeded(ScanRunState run) {
        if (run.claimPausePersistence()) {
            contentScanOrchestrator.pauseScanAfterOutage(run.scanId());
        }
    }

    /**
     * Prefers the paused-run error over a raw exception message.
     *
     * <p>The pause error names the actual cause — detector down, network down — which
     * is what lets the dashboard tell the operator the scan is resumable. A bare
     * exception message would be shown as one more scan error.
     *
     * @param run      the run whose pause cause takes precedence
     * @param fallback cause to report while the run is healthy
     * @return the error to stream
     */
    protected static TranslatableError pausedErrorOr(ScanRunState run, String fallback) {
        return run.pauseError().orElseGet(() ->
            new TranslatableError(ScanErrorKeys.UNEXPECTED,
                                  Map.of(CAUSE_PARAM, fallback == null ? "" : fallback)));
    }

    private Flux<ConfluenceContentScanResult> buildScanResultFluxBody(ScanRunState run, String spaceKey,
                                                                      List<ConfluencePage> pages, int analyzedOffset,
                                                                      int originalTotal, int total) {
        log.info("[SCAN][CONCURRENCY] space={} pages={} pageConcurrency={}", spaceKey, total, pageConcurrency);
        return Flux.fromIterable(pages)
            // index() stamps each page with its deterministic 0-based startingPosition in
            // SOURCE order BEFORE the concurrent region, so the per-page progress
            // index stays stable regardless of pageConcurrency. Pages run through an
            // unordered flatMap so a finished page frees its slot at once, and their
            // events are re-emitted in source order by inSourceOrder (checkpoint/SSE
            // ordering). flatMapSequential used to keep a finished page in its slot
            // until every earlier page had been emitted, which left the detector idle
            // whenever a page with a large attachment was ahead in the space.
            .index()
            .publishOn(Schedulers.boundedElastic())
            // Stop feeding pages once an outage was detected: they would fail the same
            // way, and each failure would emit events for work that was never analysed.
            // Pages already in flight finish and are held back individually below.
            .takeWhile(indexed -> !run.mustPause())
            .flatMap(indexed -> {
                int currentIndex = (int) (indexed.getT1() + 1);
                ConfluencePage page = indexed.getT2();
                PageScanState pageState = new PageScanState(run);
                return toAttachmentsMono(page.id())
                    .flatMapMany(attachments -> {
                        ConfluencePageContext confluencePageContext = new ConfluencePageContext(run.scanId(),
                                                                                                spaceKey,
                                                                                                page.id(),
                                                                                                page.title());
                        ScanProgress scanProgress = new ScanProgress(currentIndex, analyzedOffset,
                                                                    originalTotal, total);
                        return processPageStream(pageState, confluencePageContext, page, attachments,
                                                 scanProgress);
                    })
                    .onErrorResume(exception -> {
                        log.error(
                            "[ATTACHMENTS][USECASE] Erreur récupération pièces jointes page {}: {}",
                            page.id(), exception.getMessage());
                        // Listing attachments hits Confluence, so this is where a dropped
                        // network or VPN shows up first — classify before falling back.
                        pageState.reportError(ScanErrorClassifier.classify(exception),
                                              resolveErrorMessage(exception));
                        ScanProgress scanProgress = new ScanProgress(currentIndex, analyzedOffset,
                                                                    originalTotal, total);
                        return processOnePage(pageState, spaceKey, page, scanProgress);
                    })
                    .collectList()
                    .map(events -> Tuples.of(indexed.getT1(), events));
            }, pageConcurrency)
            .onErrorContinue((exception, ignoredElement) -> {
                log.error("[USECASE] Erreur lors du traitement d'une page: {}", exception.getMessage(),
                          exception);
                // Last-resort net: an outage reaching this point must still pause the scan
                // rather than be swallowed page after page.
                run.requestPause(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
            })
            .transform(AbstractStreamConfluenceScanUseCase::inSourceOrder)
            .concatMapIterable(Function.identity());
    }

    /**
     * Re-emits per-page results in source order while the pages themselves finish in any order.
     *
     * <p>Each result is keyed by its page's source index and released once every earlier page
     * has been released. A page dropped upstream (onErrorContinue) leaves a hole that would
     * hold back everything behind it, so whatever is still pending when the source completes
     * is released at that point, still in source order. The state lives inside the deferred
     * subscription and is only touched from the serialized onNext signals, then once after
     * completion.
     */
    static <T> Flux<T> inSourceOrder(Flux<Tuple2<Long, T>> completed) {
        return Flux.defer(() -> {
            TreeMap<Long, T> pending = new TreeMap<>();
            AtomicLong nextIndex = new AtomicLong();
            return completed
                .concatMapIterable(result -> {
                    pending.put(result.getT1(), result.getT2());
                    List<T> released = new ArrayList<>();
                    while (!pending.isEmpty() && pending.firstKey().longValue() == nextIndex.get()) {
                        released.add(pending.pollFirstEntry().getValue());
                        nextIndex.incrementAndGet();
                    }
                    return released;
                })
                .concatWith(Flux.defer(() -> Flux.fromIterable(List.copyOf(pending.values()))));
        });
    }

    private Mono<List<AttachmentInfo>> toAttachmentsMono(String pageId) {
        var future = confluenceAccessor.getPageAttachments(pageId);
        return future != null ? Mono.fromFuture(future) : Mono.just(List.of());
    }

    private Flux<ConfluenceContentScanResult> processPageStream(PageScanState pageState,
                                                                ConfluencePageContext confluencePageContext,
                                                                ConfluencePage page,
                                                                List<AttachmentInfo> attachments,
                                                                ScanProgress scanProgress) {
        if (attachments.isEmpty()) {
            log.debug("[ATTACHMENTS][USECASE] Aucune pièce jointe pour la page {} - {}", page.id(),
                      page.title());
            return processOnePage(pageState, confluencePageContext.spaceKey(), page, scanProgress);
        }
        attachments.forEach(attachment -> log.info(
            "[ATTACHMENTS][USECASE] pageId={} title=\"{}\" name=\"{}\" ext=\"{}\"",
            page.id(), page.title(), attachment.name(), attachment.extension()));

        return attachmentsFlux(pageState, confluencePageContext.spaceKey(), page, attachments,
                               scanProgress)
            .concatWith(
                processOnePage(pageState, confluencePageContext.spaceKey(), page, scanProgress));
    }

    private Flux<ConfluenceContentScanResult> attachmentsFlux(PageScanState pageState, String spaceKey,
                                                              ConfluencePage page,
                                                              List<AttachmentInfo> attachments,
                                                              ScanProgress scanProgress) {
        return attachmentProcessor.extractAttachmentsText(page.id(), attachments)
            .flatMap(extracted -> analyzeAttachmentText(pageState, spaceKey, page, extracted,
                                                       scanProgress));
    }

    private Mono<ConfluenceContentScanResult> analyzeAttachmentText(PageScanState pageState, String spaceKey,
                                                                    ConfluencePage page,
                                                                    AttachmentTextExtracted extracted,
                                                                    ScanProgress scanProgress) {
        return Mono.fromCallable(() -> detectPii(extracted.extractedText()))
        .flatMap(detection -> {
            reportUnavailableDetector(pageState, detection);
            // Same reason as for a page: a partial analysis is not persisted, because the
            // whole page (attachments included) is re-scanned on resume.
            if (pageState.hitOutage()) {
                return Mono.empty();
            }
            double progress = calculateProgressForAttachment(scanProgress);
            return Mono.just(contentScanOrchestrator.createAttachmentItemEvent(
                pageState.scanId(), spaceKey, page, extracted.attachment(), extracted.extractedText(), detection,
                progress));
        })
        .timeout(scanTimeoutConfig.getPiiDetectionTimeout())
        .onErrorResume(TimeoutException.class, _ -> {
            log.warn("[TIMEOUT][REACTOR] Space={}, PageId={}, AttachmentName=\"{}\", ReactorTimeout exceeded",
                    spaceKey, page.id(), extracted.attachment().name());

            // Expected a few times per full scan on oversized attachments: an item
            // failure, never a reason to pause the whole scan.
            pageState.reportError(ScanErrorKind.ITEM_FAILURE, null);
            double progress = calculateProgressForAttachment(scanProgress);

            return Mono.just(contentScanOrchestrator.createErrorEvent(
                pageState.scanId(), spaceKey, page.id(),
                new TranslatableError(ScanErrorKeys.ATTACHMENT_TIMEOUT,
                                      Map.of("attachment", extracted.attachment().name())),
                progress));
        })
        .onErrorResume(exception -> {
            // Try to find StatusRuntimeException in the cause chain
            StatusRuntimeException grpcException = findGrpcException(exception);

            if (grpcException != null) {
                return handleGrpcError(pageState, spaceKey, page, extracted.attachment(), scanProgress, grpcException);
            }

            // Fallback: general error handling
            log.error("[ERROR][GENERAL] Space={}, PageId={}, AttachmentName=\"{}\", Error analyzing attachment",
                      spaceKey, page.id(), extracted.attachment().name(), exception);

            pageState.reportError(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
            double progress = calculateProgressForAttachment(scanProgress);

            return Mono.just(contentScanOrchestrator.createErrorEvent(
                pageState.scanId(), spaceKey, page.id(),
                new TranslatableError(ScanErrorKeys.ATTACHMENT_FAILED,
                                      Map.of("attachment", extracted.attachment().name(),
                                             CAUSE_PARAM, resolveErrorMessage(exception))),
                progress));
        });
    }


    private Flux<ConfluenceContentScanResult> processOnePage(PageScanState pageState, String spaceKey,
                                                             ConfluencePage page,
                                                             ScanProgress scanProgress) {
        String scanId = pageState.scanId();
        String rawContent = extractPageContent(page);
        String content = htmlContentParser.cleanText(rawContent);
        double startProgress = contentScanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
            scanProgress.originalTotal());
        ConfluenceContentScanResult pageStart = contentScanOrchestrator.createPageStartEvent(scanId, spaceKey, page,
                                                                                             scanProgress.currentIndex(),
                                                                                             scanProgress.originalTotal(), startProgress);

        Flux<ConfluenceContentScanResult> itemEvent = createPageItemEvent(pageState, spaceKey, page, content, scanProgress);

        double completeProgress = contentScanOrchestrator.calculateProgress(scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                                                                            scanProgress.originalTotal());
        ConfluenceContentScanResult pageComplete = contentScanOrchestrator.createPageCompleteEvent(scanId, spaceKey, page,
                                                                                                   completeProgress);
        // Deferred and conditional: pageComplete advances the checkpoint past this page,
        // so emitting it after an outage would make the resume skip a page that was never
        // analysed — the silent false negative this whole mechanism exists to prevent.
        // An ordinary item failure still emits it: the item is reported as failed, and
        // holding the checkpoint back would re-scan the page and double-count its findings.
        Flux<ConfluenceContentScanResult> pageCompleteEvent = Flux.defer(() ->
            pageState.hitOutage() ? Flux.empty() : Flux.just(pageComplete));

        return Flux.just(pageStart)
            .concatWith(itemEvent)
            .concatWith(pageCompleteEvent)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ConfluenceContentScanResult> createPageItemEvent(PageScanState pageState, String spaceKey,
                                                                  ConfluencePage page,
                                                                  String content, ScanProgress scanProgress) {
        String scanId = pageState.scanId();
        if (isBlank(content)) {
            return createEmptyPageItem(scanId, spaceKey, page, scanProgress);
        }

        return Mono.fromCallable(() -> detectPii(content))
            .timeout(scanTimeoutConfig.getPiiDetectionTimeout())
            .flatMap(detection -> {
                reportUnavailableDetector(pageState, detection);
                // A detector that did not run makes this analysis partial. Emitting it would
                // persist its findings and severity counts, and the resume — which re-scans
                // this page, since its pageComplete is held back — would count them twice.
                if (pageState.hitOutage()) {
                    return Mono.empty();
                }
                return Mono.just(buildPageItemEvent(scanId, page, content, detection, scanProgress));
            })
            .onErrorResume(exception -> {
                if (exception instanceof TimeoutException) {
                    return handleReactorTimeoutError(pageState, spaceKey, page, scanProgress);
                }

                // Try to find StatusRuntimeException in the cause chain
                StatusRuntimeException grpcException = findGrpcException(exception);

                if (grpcException != null) {
                    return handleGrpcError(pageState, spaceKey, page, null, scanProgress, grpcException);
                }

                // Fallback: general error handling
                return handleDetectionError(pageState, spaceKey, page, scanProgress, exception);
            })
            .flux();
    }

    /**
     * Pauses the scan when a detector could not run although the analysis call itself
     * succeeded.
     *
     * <p><strong>Business rule:</strong> this is the silent case, and the most damaging
     * one. When the remote model endpoint is down, the detection service still answers
     * with the findings of the detectors that did run, so the page is recorded as
     * analysed and its report reads as clean. Continuing would produce a report an
     * operator cannot tell apart from a genuinely clean space.
     */
    private void reportUnavailableDetector(PageScanState pageState, ContentPiiDetection detection) {
        ScanErrorClassifier.unavailableDetector(detection)
            .ifPresent(cause -> pageState.reportError(ScanErrorKind.DETECTOR_UNAVAILABLE, cause));
    }

    private Flux<ConfluenceContentScanResult> createEmptyPageItem(String scanId, String spaceKey,
                                                                  ConfluencePage page,
                                                                  ScanProgress scanProgress) {
        double progress = calculateProgressForCurrentItem(scanProgress);
        ConfluenceContentScanResult event = contentScanOrchestrator.createEmptyPageItemEvent(scanId, spaceKey, page, progress);
        return Flux.just(event);
    }

    private ConfluenceContentScanResult buildPageItemEvent(String scanId, ConfluencePage page,
                                                           String content, ContentPiiDetection detection,
                                                           ScanProgress scanProgress) {
        double progress = calculateProgressForCurrentItem(scanProgress);
        return contentScanOrchestrator.createPageItemEvent(scanId, page.spaceKey(), page, content, detection,
                                                           progress);
    }

    private Mono<ConfluenceContentScanResult> handleReactorTimeoutError(PageScanState pageState, String spaceKey,
                                                                        ConfluencePage page,
                                                                        ScanProgress scanProgress) {
        log.warn("[TIMEOUT][REACTOR] Space={}, PageId={}, PageTitle=\"{}\", ReactorTimeout exceeded",
                spaceKey, page.id(), page.title());

        // A detection that outran its timeout says nothing about the detector's health:
        // expected on very large pages, so the scan carries on with the item reported failed.
        pageState.reportError(ScanErrorKind.ITEM_FAILURE, null);
        double progress = calculateProgressForCurrentItem(scanProgress);

        ConfluenceContentScanResult errorEvent = contentScanOrchestrator.createErrorEvent(
            pageState.scanId(), spaceKey, page.id(),
            new TranslatableError(ScanErrorKeys.PAGE_TIMEOUT, Map.of("page", page.title())),
            progress);

        return Mono.just(errorEvent);
    }

    private Mono<ConfluenceContentScanResult> handleGrpcError(PageScanState pageState, String spaceKey,
                                                              ConfluencePage page,
                                                              AttachmentInfo attachment,
                                                              ScanProgress scanProgress,
                                                              StatusRuntimeException exception) {
        String targetType = attachment != null ? "Attachment" : "Page";
        String targetName = attachment != null ? attachment.name() : page.title();
        String targetIdentifier = attachment != null ? page.id() + "/" + attachment.name() : page.id();
        boolean isDeadlineExceeded = exception.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED;

        if (isDeadlineExceeded) {
            log.warn("[TIMEOUT][GRPC_DEADLINE_EXCEEDED] Space={}, {}=\"{}\", Identifier={}, gRPC deadline exceeded",
                    spaceKey, targetType, targetName, targetIdentifier);
        } else {
            log.error("[ERROR][GRPC] Space={}, {}=\"{}\", Identifier={}, gRPC error: {} - {}",
                    spaceKey, targetType, targetName, targetIdentifier,
                    exception.getStatus().getCode(), exception.getMessage());
        }

        ScanErrorKind kind = ScanErrorClassifier.classify(exception);
        pageState.reportError(kind, "Detection service unreachable (gRPC "
            + exception.getStatus().getCode() + ")");

        double progress = calculateProgressForCurrentItem(scanProgress);
        TranslatableError error = isDeadlineExceeded
                ? new TranslatableError(ScanErrorKeys.DETECTION_TIMEOUT, Map.of("item", targetName))
                : new TranslatableError(ScanErrorKeys.DETECTION_FAILED,
                                        Map.of("status", exception.getStatus().getCode().name()));

        return Mono.just(contentScanOrchestrator.createErrorEvent(pageState.scanId(), spaceKey, page.id(),
                                                                  error, progress));
    }

    private Mono<ConfluenceContentScanResult> handleDetectionError(PageScanState pageState, String spaceKey,
                                                                   ConfluencePage page,
                                                                   ScanProgress scanProgress,
                                                                   Throwable exception) {
        log.error("[ERROR][GENERAL] Space={}, PageId={}, PageTitle=\"{}\", Error analyzing page",
                 spaceKey, page.id(), page.title(), exception);

        pageState.reportError(ScanErrorClassifier.classify(exception), resolveErrorMessage(exception));
        double progress = calculateProgressForCurrentItem(scanProgress);

        ConfluenceContentScanResult errorEvent = contentScanOrchestrator.createErrorEvent(
            pageState.scanId(), spaceKey, page.id(),
            new TranslatableError(ScanErrorKeys.PAGE_FAILED,
                                  Map.of("page", page.title() == null ? "" : page.title(),
                                         CAUSE_PARAM, resolveErrorMessage(exception))),
            progress);

        return Mono.just(errorEvent);
    }

    private String extractPageContent(ConfluencePage page) {
        return page.content() != null ? page.content().body() : "";
    }

    private ContentPiiDetection detectPii(String content) {
        String safeContent = content != null ? content : "";
        int charCount = safeContent.length();
        // Snapshot the concurrent detection count for this call so the throughput
        // log shows whether pageConcurrency is actually exercised (should approach
        // the configured value once the worker pool is fed in parallel).
        int observedInflight = inflightDetections.incrementAndGet();
        long startTime = System.currentTimeMillis();
        ContentPiiDetection contentPiiDetection;
        try {
            contentPiiDetection = piiDetectorClient.analyzeContent(safeContent);
        } finally {
            inflightDetections.decrementAndGet();
        }
        long duration = System.currentTimeMillis() - startTime;

        // Structured [THROUGHPUT] tag, aligned with the Python format
        // ([THROUGHPUT] phase=detection ...). Emitted on a parallel scheduler so
        // the Reactor pipeline never blocks on logging.
        ContentPiiDetection detectionForLog = contentPiiDetection;
        Mono.fromRunnable(() -> {
                    double charsPerSecond = duration > 0 ? (charCount * 1000.0) / duration : 0;
                    log.info("[THROUGHPUT] phase=detection chars={} duration_ms={} chars_per_s={} inflight={}",
                            charCount,
                            duration,
                            String.format(Locale.ROOT, "%.2f", charsPerSecond),
                            observedInflight);
                    if (log.isDebugEnabled()) {
                        log.debug("Content: {}", safeContent);
                        log.debug("Pii content: {}", detectionForLog);
                    }
                })
                .subscribeOn(Schedulers.parallel())
                .subscribe();

        return contentPiiDetection;
    }

    private ConfluenceContentScanResult suppressFalsePositives(ConfluenceContentScanResult event,
                                                               Set<String> falsePositiveIds) {
        if (falsePositiveSuppressor == null) {
            return event;
        }
        return falsePositiveSuppressor.suppress(event, falsePositiveIds);
    }

    boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Calculates progress for the current item being processed.
     * Used when an item is completed or encounters an error during processing.
     */
    private double calculateProgressForCurrentItem(ScanProgress scanProgress) {
        return contentScanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
            scanProgress.originalTotal());
    }

    /**
     * Calculates progress for an attachment being processed (before page content).
     * Uses currentIndex - 1 because attachments are processed before the page item itself.
     */
    private double calculateProgressForAttachment(ScanProgress scanProgress) {
        return contentScanOrchestrator.calculateProgress(
            scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
            scanProgress.originalTotal());
    }

    /**
     * Pre-flight event emitted when an enabled detector cannot be reached.
     *
     * <p><strong>Business rule:</strong> a detector an operator switched on but
     * whose backing endpoint is down contributes zero findings, which is
     * indistinguishable from a clean space. Rather than producing a silently
     * incomplete report, the scan is refused and the reason is streamed to the
     * dashboard.
     *
     * <p>The check itself is delegated to the detection service, because the
     * detector endpoints are configured from that service's network standpoint.
     * When the check cannot be performed at all (detection service down, older
     * image without the health RPC) the scan proceeds: refusing it would trade a
     * silent failure for an unexplained blockage, and per-page detection errors
     * already surface a dead detection service.
     *
     * <p>One event per unreachable detector: each one is a distinct thing to fix,
     * and merging them into a single sentence would hide all but the first.
     *
     * @param scanId   identifier correlating the events of this scan
     * @param spaceKey space the scan was requested for, may be null for multi-space scans
     * @return the blocking error events, empty when every enabled detector is ready
     */
    protected List<ConfluenceContentScanResult> detectorPreflightFailures(String scanId, String spaceKey) {
        List<DetectorHealth> health;
        try {
            health = piiDetectorClient.checkDetectorsHealth();
        } catch (Exception exception) {
            log.warn("[PREFLIGHT] Unable to verify detector availability, starting scan anyway: {}",
                     resolveErrorMessage(exception));
            return List.of();
        }

        List<DetectorHealth> unreachable = health.stream()
            .filter(detector -> !detector.reachable())
            .toList();
        if (unreachable.isEmpty()) {
            log.info("[PREFLIGHT] All {} enabled detector(s) reachable for scan={}", health.size(), scanId);
            return List.of();
        }

        log.error("[PREFLIGHT] Refusing scan={} space={}: {}", scanId, spaceKey,
                  unreachable.stream().map(DetectorHealth::describeFailure)
                      .collect(Collectors.joining(" | ")));
        return unreachable.stream()
            .map(detector -> contentScanOrchestrator.createErrorEvent(
                scanId, spaceKey, null, detector.failure(), 0.0))
            .toList();
    }

    /**
     * Resolves a human-readable error message from an exception, handling cases where
     * getMessage() returns null (e.g., java.net.ConnectException).
     */
    protected static String resolveErrorMessage(Throwable exception) {
        if (exception instanceof ConnectException) {
            return "Unable to connect to the data source. Verify the server is reachable and the URL is correct.";
        }
        if (exception instanceof UnknownHostException) {
            String host = exception.getMessage();
            return host != null
                ? "Unknown host: " + host + ". Verify the server URL."
                : "Unknown host. Verify the server URL.";
        }
        if (exception instanceof SocketTimeoutException) {
            return "Connection timed out. The server may be unreachable or overloaded.";
        }
        String message = exception.getMessage();
        return message != null ? message : exception.getClass().getSimpleName();
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
        return ScanErrorClassifier.findInCauseChain(throwable, StatusRuntimeException.class);
    }
}
