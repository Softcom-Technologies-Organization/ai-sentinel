package pro.softcom.aisentinel.application.jira.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.jira.port.in.StreamJiraScanPort;
import pro.softcom.aisentinel.application.jira.service.JiraAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.reporting.usecase.DetectionReportingEventType;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.jira.JiraIssue;
import pro.softcom.aisentinel.domain.jira.JiraProject;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.domain.pii.reporting.ContentScanResult;
import pro.softcom.aisentinel.domain.pii.scan.ScanProgress;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Use case orchestrating Jira scans and PII detection.
 * Returns a reactive stream of scan events for SSE consumption.
 */
@RequiredArgsConstructor
@Slf4j
public class StreamJiraScanUseCase implements StreamJiraScanPort {

    private final JiraAccessor jiraAccessor;
    private final PiiDetectorClient piiDetectorClient;
    private final ContentScanOrchestrator contentScanOrchestrator;
    private final PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestrator;

    @Override
    public Flux<ContentScanResult> scanAllProjects() {
        String scanId = UUID.randomUUID().toString();
        log.info("[JIRA-SCAN] Creating new scan with scanId: {}", scanId);

        contentScanOrchestrator.purgePreviousScanData();

        Flux<ContentScanResult> header = buildHeader(scanId);
        Flux<ContentScanResult> body = buildAllProjectsScanBody(scanId);
        Flux<ContentScanResult> footer = buildFooter(scanId);

        Flux<ContentScanResult> scanFlux = Flux.concat(header, body, footer);

        scanExecutionOrchestrator.startScan(scanId, scanFlux);
        return scanExecutionOrchestrator.subscribeScan(scanId);
    }

    @Override
    public Flux<ContentScanResult> scanSelectedProjects(List<String> projectKeys) {
        String scanId = UUID.randomUUID().toString();
        log.info("[JIRA-SCAN] Creating new selected projects scan with scanId: {}", scanId);

        contentScanOrchestrator.purgePreviousScanDataForSpaces(projectKeys);

        Flux<ContentScanResult> header = buildHeader(scanId);
        Flux<ContentScanResult> body = buildSelectedProjectsScanBody(scanId, projectKeys);
        Flux<ContentScanResult> footer = buildFooter(scanId);

        Flux<ContentScanResult> scanFlux = Flux.concat(header, body, footer);

        scanExecutionOrchestrator.startScan(scanId, scanFlux);
        return scanExecutionOrchestrator.subscribeScan(scanId);
    }

    private Flux<ContentScanResult> buildAllProjectsScanBody(String scanId) {
        return Mono.fromFuture(jiraAccessor.getAllProjects())
                .flatMapMany(projects -> {
                    Flux<ContentScanResult> errorFlux = createErrorIfNoProjects(scanId, projects);
                    return Objects.requireNonNullElseGet(errorFlux, () -> createProjectScanFlux(scanId, projects));
                })
                .onErrorResume(exception -> {
                    log.error("[JIRA-SCAN] Error in scan flux: {}", exception.getMessage(), exception);
                    return errorEvent(scanId, null, exception.getMessage());
                });
    }

    private Flux<ContentScanResult> buildSelectedProjectsScanBody(String scanId, List<String> projectKeys) {
        return Mono.fromFuture(jiraAccessor.getAllProjects())
                .flatMapMany(allProjects -> {
                    List<JiraProject> selected = allProjects.stream()
                            .filter(p -> projectKeys.contains(p.key()))
                            .toList();

                    Flux<ContentScanResult> errorFlux = createErrorIfNoProjects(scanId, selected);
                    return Objects.requireNonNullElseGet(errorFlux, () -> createProjectScanFlux(scanId, selected));
                })
                .onErrorResume(exception -> {
                    log.error("[JIRA-SCAN] Error in selected projects scan flux: {}", exception.getMessage(), exception);
                    return errorEvent(scanId, null, exception.getMessage());
                });
    }

    private Flux<ContentScanResult> createProjectScanFlux(String scanId, List<JiraProject> projects) {
        return Flux.fromIterable(projects)
                .concatMap(project -> scanProject(scanId, project)
                        .onErrorResume(exception -> {
                            log.error("[JIRA-SCAN] Error during project scan {}: {}",
                                    project.key(), exception.getMessage(), exception);
                            return errorEvent(scanId, project.key(), exception.getMessage());
                        }));
    }

    private Flux<ContentScanResult> scanProject(String scanId, JiraProject project) {
        return Mono.fromFuture(jiraAccessor.getIssuesInProject(project.key()))
                .flatMapMany(issues -> {
                    int total = issues.size();
                    AtomicInteger index = new AtomicInteger(0);

                    Flux<ContentScanResult> startEvent = Flux.just(
                            contentScanOrchestrator.createStartEvent(scanId, project.key(), total, 0.0));

                    Flux<ContentScanResult> issueEvents = Flux.fromIterable(issues)
                            .publishOn(Schedulers.boundedElastic())
                            .concatMap(issue -> {
                                int currentIndex = index.incrementAndGet();
                                ScanProgress progress = new ScanProgress(currentIndex, 0, total, total);
                                return processIssue(scanId, project.key(), issue, progress);
                            })
                            .onErrorContinue((exception, ignoredElement) -> log.error(
                                    "[JIRA-SCAN] Error processing issue: {}", exception.getMessage(), exception));

                    Flux<ContentScanResult> completeEvent = Flux.just(
                            contentScanOrchestrator.createCompleteEvent(scanId, project.key()));

                    return Flux.concat(startEvent, issueEvents, completeEvent)
                            .doOnEach(signal -> {
                                if (signal.isOnNext() && signal.get() != null) {
                                    ContentScanResult event = signal.get();
                                    contentScanOrchestrator.persistCheckpointSynchronously(event);
                                    Mono.fromRunnable(() -> contentScanOrchestrator.persistEventAsyncOperations(event, SourceType.JIRA))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                                        .onErrorResume(e -> {
                                            log.warn("[JIRA-SCAN] Failed to persist async operations: {}", e.getMessage());
                                            return Mono.empty();
                                        })
                                        .subscribe();
                                }
                            });
                });
    }

    private Flux<ContentScanResult> processIssue(String scanId, String projectKey,
                                                  JiraIssue issue, ScanProgress scanProgress) {
        String content = issue.getContentBody();
        double startProgress = contentScanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + (scanProgress.currentIndex() - 1),
                scanProgress.originalTotal());

        ContentScanResult issueStart = contentScanOrchestrator.createContentStartEvent(
                scanId, projectKey, issue, scanProgress.currentIndex(), scanProgress.originalTotal(), startProgress);

        Flux<ContentScanResult> itemEvent = createIssueItemEvent(scanId, projectKey, issue, content, scanProgress);

        double completeProgress = contentScanOrchestrator.calculateProgress(
                scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                scanProgress.originalTotal());
        ContentScanResult issueComplete = contentScanOrchestrator.createContentCompleteEvent(
                scanId, projectKey, issue, completeProgress);

        return Flux.just(issueStart)
                .concatWith(itemEvent)
                .concatWith(Mono.just(issueComplete))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ContentScanResult> createIssueItemEvent(String scanId, String projectKey,
                                                          JiraIssue issue, String content,
                                                          ScanProgress scanProgress) {
        if (content == null || content.isBlank()) {
            double progress = contentScanOrchestrator.calculateProgress(
                    scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                    scanProgress.originalTotal());
            return Flux.just(contentScanOrchestrator.createEmptyContentItemEvent(
                    scanId, projectKey, issue, progress));
        }

        return Mono.fromCallable(() -> piiDetectorClient.analyzeContent(content))
                .map(detection -> {
                    double progress = contentScanOrchestrator.calculateProgress(
                            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                            scanProgress.originalTotal());
                    return contentScanOrchestrator.createContentItemEvent(
                            scanId, projectKey, issue, content, detection, progress);
                })
                .onErrorResume(exception -> {
                    log.error("[JIRA-SCAN] Error analyzing issue {}: {}", issue.key(), exception.getMessage());
                    double progress = contentScanOrchestrator.calculateProgress(
                            scanProgress.analyzedOffset() + scanProgress.currentIndex(),
                            scanProgress.originalTotal());
                    return Mono.just(contentScanOrchestrator.createErrorEvent(
                            scanId, projectKey, issue.id(),
                            "Error analyzing issue: " + exception.getMessage(), progress));
                })
                .flux();
    }

    private static Flux<ContentScanResult> buildHeader(String scanId) {
        return Flux.just(ContentScanResult.builder()
                .scanId(scanId)
                .eventType(DetectionReportingEventType.MULTI_START.getLabel())
                .emittedAt(Instant.now().toString())
                .build());
    }

    private static Flux<ContentScanResult> buildFooter(String scanId) {
        return Flux.just(ContentScanResult.builder()
                .scanId(scanId)
                .eventType(DetectionReportingEventType.MULTI_COMPLETE.getLabel())
                .emittedAt(Instant.now().toString())
                .build());
    }

    private static Flux<ContentScanResult> createErrorIfNoProjects(String scanId, List<JiraProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return errorEvent(scanId, null, "No project found");
        }
        return null;
    }

    private static Flux<ContentScanResult> errorEvent(String scanId, String sourceId, String message) {
        return Flux.just(ContentScanResult.builder()
                .scanId(scanId)
                .sourceId(sourceId)
                .eventType(DetectionReportingEventType.ERROR.getLabel())
                .message(message)
                .emittedAt(Instant.now().toString())
                .build());
    }
}
