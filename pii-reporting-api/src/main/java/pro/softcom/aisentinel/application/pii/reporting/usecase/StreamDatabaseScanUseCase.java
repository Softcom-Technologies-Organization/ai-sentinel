package pro.softcom.aisentinel.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamDatabaseScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.scan.port.out.LoadContentPort;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.domain.pii.reporting.ContentScanResult;
import pro.softcom.aisentinel.domain.pii.scan.model.ScanSourceConfig;
import pro.softcom.aisentinel.domain.pii.scan.model.ScannableContent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Slf4j
public class StreamDatabaseScanUseCase implements StreamDatabaseScanPort {

    private final LoadContentPort loadContentPort;
    private final PiiDetectorClient piiDetectorClient;
    private final ContentScanOrchestrator contentScanOrchestrator;
    private final ScanTimeOutConfig scanTimeOutConfig;

    @Override
    public Flux<ContentScanResult> streamScan(ScanSourceConfig config) {
        String sourceId = config.properties().getOrDefault("table", "unknown-db-source");
        String scanId = UUID.randomUUID().toString();
        log.info("[SCAN][DB] Starting database scan for sourceId: {}, scanId: {}, type: {}", sourceId, scanId, config.type());

        // We collect list to know total count for progress calculation.
        // Since current adapter loads everything in memory anyway, this is safe for now.
        return loadContentPort.loadContent(config)
                .collectList()
                .flatMapMany(contentList -> {
                    if (contentList.isEmpty()) {
                        log.warn("[SCAN][DB] No content found for sourceId: {}", sourceId);
                        return Flux.just(ContentScanResult.builder()
                                .scanId(scanId)
                                .sourceId(sourceId)
                                .eventType(DetectionReportingEventType.ERROR.getLabel())
                                .message("No content found for source: " + sourceId)
                                .emittedAt(Instant.now().toString())
                                .build());
                    }
                    return runScanFlux(scanId, sourceId, contentList);
                })
                .onErrorResume(e -> {
                    log.error("[SCAN][DB] Error during scan initialization: {}", e.getMessage(), e);
                    return Flux.just(ContentScanResult.builder()
                            .scanId(scanId)
                            .sourceId(sourceId)
                            .eventType(DetectionReportingEventType.ERROR.getLabel())
                            .message("Scan failed: " + e.getMessage())
                            .emittedAt(Instant.now().toString())
                            .build());
                });
    }

    private Flux<ContentScanResult> runScanFlux(String scanId, String sourceId, List<ScannableContent> contentList) {
        int total = contentList.size();
        AtomicInteger processedCount = new AtomicInteger(0);

        Flux<ContentScanResult> startEvent = Flux.just(contentScanOrchestrator.createStartEvent(scanId, sourceId, total, 0.0));

        Flux<ContentScanResult> itemEvents = Flux.fromIterable(contentList)
                .concatMap(content -> processContentItem(scanId, sourceId, content, processedCount, total));

        Flux<ContentScanResult> completeEvent = Flux.defer(() -> 
            Flux.just(contentScanOrchestrator.createCompleteEvent(scanId, sourceId))
        );

        return Flux.concat(startEvent, itemEvents, completeEvent)
                .doOnNext(event -> contentScanOrchestrator.persistEventAsyncOperations(event, SourceType.DATABASE));
    }

    private Mono<ContentScanResult> processContentItem(String scanId, String sourceId, ScannableContent content,
                                                       AtomicInteger processedCount, int total) {
        return Mono.fromCallable(() -> {
                    String text = content.getContentBody();
                    if (text == null) text = "";
                    return piiDetectorClient.analyzeContent(text);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(scanTimeOutConfig.getPiiDetection())
                .map(detection -> {
                    int current = processedCount.incrementAndGet();
                    double progress = contentScanOrchestrator.calculateProgress(current, total);
                    return contentScanOrchestrator.createContentItemEvent(scanId, sourceId, content, content.getContentBody(), detection, progress);
                })
                .onErrorResume(e -> {
                    int current = processedCount.incrementAndGet();
                    double progress = contentScanOrchestrator.calculateProgress(current, total);
                    log.error("[SCAN][DB] Error processing content {}: {}", content.getId(), e.getMessage());
                    return Mono.just(contentScanOrchestrator.createErrorEvent(scanId, sourceId, content.getId(),
                            "Error processing content: " + e.getMessage(), progress));
                });
    }
}
