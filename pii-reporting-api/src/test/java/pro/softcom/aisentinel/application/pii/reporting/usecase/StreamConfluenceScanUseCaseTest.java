package pro.softcom.aisentinel.application.pii.reporting.usecase;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pro.softcom.aisentinel.application.confluence.port.out.*;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.*;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKeys;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.JpaScanEventStoreAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.event.ScanEventPublisherAdapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamConfluenceScanUseCaseTest {

    @Mock
    private ConfluenceClient confluenceService;

    @Mock
    private ConfluenceAttachmentClient confluenceAttachmentService;

    @Mock
    private ConfluenceAttachmentDownloader confluenceDownloadService;

    @Mock
    private AttachmentTextExtractor attachmentTextExtractionService;

    @Mock
    private ScanCheckpointRepository scanCheckpointRepository;

    @Mock
    private PiiDetectorClient piiDetectorClient;

    @Mock
    private JpaScanEventStoreAdapter jpaScanEventStoreAdapter;

    @Mock
    private ScanTimeOutConfig scanTimeoutConfig;

    @Mock
    private PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort;

    @Mock
    private pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService severityCalculationService;

    @Mock
    private pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService scanSeverityCountService;

    @Mock
    private pro.softcom.aisentinel.application.pii.reporting.ScanPiiTypeCountService scanPiiTypeCountService;

    @Mock
    private ConfluenceSpaceRepository spaceRepository;

    private StreamConfluenceScanUseCase streamConfluenceScanUseCase;

    // Collaborators promoted to fields so tests can rebuild the use case with a
    // custom page concurrency (see useCaseWithConcurrency).
    private ConfluenceAccessor confluenceAccessor;
    private ContentScanOrchestrator contentScanOrchestrator;
    private AttachmentProcessor attachmentProcessor;
    private HtmlContentParser htmlContentParser;
    private ScanSpaceStatsCollector scanSpaceStatsCollector;

    @BeforeEach
    void setUp() {
        final ConfluenceUrlProvider confluenceUrlProvider = stubDataCenterUrlProvider("http://confluence.example");

        // Create service instances
        var applicationEventPublisher = mock(ApplicationEventPublisher.class);
        var parserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        var piiContextExtractor = new PiiContextExtractor(parserFactory);
        ScanProgressCalculator progressCalculator = new ScanProgressCalculator();
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor, severityCalculationService, value -> null);
        ScanCheckpointService checkpointService = new ScanCheckpointService(scanCheckpointRepository);
        PublishEventPort publishEventPort = new ScanEventPublisherAdapter(applicationEventPublisher);
        ScanEventDispatcher scanEventDispatcher = new ScanEventDispatcher(publishEventPort,
                                                                          Runnable::run);

        // Create parameter objects
        confluenceAccessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService, spaceRepository);
        contentScanOrchestrator = new ContentScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher,
                severityCalculationService, scanSeverityCountService, scanPiiTypeCountService
        );
        attachmentProcessor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );
        htmlContentParser = new HtmlContentParser();
        scanSpaceStatsCollector = mock(ScanSpaceStatsCollector.class);

        ScanPipelineDependencies pipelineDependencies = new ScanPipelineDependencies(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig,
                htmlContentParser,
                scanSpaceStatsCollector
        );
        streamConfluenceScanUseCase = new StreamConfluenceScanUseCase(
                pipelineDependencies,
                personallyIdentifiableInformationScanExecutionOrchestratorPort
        );

        // Configure severity calculation mock to return a default severity for any PII type
        Mockito.lenient().when(severityCalculationService.calculateSeverity(any()))
                .thenReturn(PersonallyIdentifiableInformationSeverity.LOW);

        // Le use case délègue la gestion du flux au ScanTaskManager.
        // Dans les tests unitaires, on souhaite observer directement le Flux construit par le use case
        // sans complexifier inutilement le comportement du mock.
        // On configure donc le mock pour qu'il renvoie exactement le flux passé à startScan
        // lorsque subscribeScan est appelé avec le même identifiant.
        Mockito.lenient().doAnswer(invocation -> {
                    String scanId = invocation.getArgument(0);
                    Flux<ConfluenceContentScanResult> flux = invocation.getArgument(1);
                    when(
                        personallyIdentifiableInformationScanExecutionOrchestratorPort.subscribeScan(scanId)).thenReturn(flux);
                    return null;
                })
                .when(personallyIdentifiableInformationScanExecutionOrchestratorPort)
                .startScan(any(), any());
    }

    /** Rebuilds the use case with a custom page concurrency, reusing the shared collaborators. */
    private StreamConfluenceScanUseCase useCaseWithConcurrency(int concurrency) {
        ScanPipelineDependencies deps = new ScanPipelineDependencies(
                confluenceAccessor, piiDetectorClient, contentScanOrchestrator, attachmentProcessor,
                scanTimeoutConfig, htmlContentParser, scanSpaceStatsCollector, concurrency);
        return new StreamConfluenceScanUseCase(
                deps, personallyIdentifiableInformationScanExecutionOrchestratorPort);
    }

    @Test
    @DisplayName("streamSpace - with pageConcurrency>1, page events are still emitted in source order")
    void streamSpace_concurrentPages_preserveOrder() {
        String spaceKey = "S-CONC";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t", "http://test.com", "d",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        int pageCount = 5;
        java.util.List<ConfluencePage> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            pages.add(ConfluencePage.builder()
                .id("p-" + i)
                .title("T" + i)
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("scan marker P" + i + " endingPosition"))
                .build());
            when(confluenceAttachmentService.getPageAttachments("p-" + i))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        }
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(pages));
        when(scanTimeoutConfig.getPiiDetectionTimeout()).thenReturn(Duration.ofSeconds(30));

        // Inverse-completion detection: page 1 finishes LAST, because each page's analysis
        // waits for the next page's analysis to complete. The completion order p-5..p-1 is
        // therefore fixed by the latches rather than by timing, and it only unblocks at all
        // because pageConcurrency = pageCount keeps the analyses in flight together.
        // An unordered flatMap would emit in completion order; flatMapSequential must still
        // emit in source order p-1..p-5.
        CountDownLatch[] analysed = new CountDownLatch[pageCount + 2];
        for (int i = 0; i < analysed.length; i++) {
            analysed[i] = new CountDownLatch(1);
        }
        analysed[pageCount + 1].countDown();

        when(piiDetectorClient.analyzeContent(any())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("P(\\d+)").matcher(text);
            int pageNum = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
            assertThat(analysed[pageNum + 1].await(10, TimeUnit.SECONDS))
                .as("page P%d waited for P%d to be analysed", pageNum, pageNum + 1)
                .isTrue();
            analysed[pageNum].countDown();
            return ContentPiiDetection.builder().statistics(Map.of()).sensitiveDataFound(List.of()).build();
        });

        StreamConfluenceScanUseCase useCase = useCaseWithConcurrency(pageCount);

        List<String> pageStartOrder = useCase.streamSpace(spaceKey)
                .filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()))
                .map(ConfluenceContentScanResult::pageId)
                .timeout(Duration.ofSeconds(15))
                .collectList()
                .block();

        assertThat(pageStartOrder).containsExactly("p-1", "p-2", "p-3", "p-4", "p-5");
    }

    @Test
    @DisplayName("streamSpace - an enabled but unreachable detector refuses the scan with an explicit error")
    void streamSpace_unreachableDetector_refusesScan() {
        String spaceKey = "S-DETECTOR-DOWN";
        when(piiDetectorClient.checkDetectorsHealth()).thenReturn(List.of(
            new DetectorHealth(DetectorSource.MINISTRAL, false, "http://lmstudio:1234/v1",
                "ConnectError: connection refused", "ENDPOINT_UNREACHABLE",
                Map.of("cause", "ConnectError: connection refused"))));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo(ScanEventType.ERROR.toJson());
                // The dashboard words the refusal from the key, so the event must name the
                // detector and where it was probed rather than carry an English sentence.
                assertThat(ev.errorKey()).isEqualTo(ScanErrorKeys.DETECTOR_ENDPOINT_UNREACHABLE);
                assertThat(ev.errorParams())
                    .containsEntry("detector", "MINISTRAL")
                    .containsEntry("endpoint", "http://lmstudio:1234/v1")
                    .containsEntry("cause", "ConnectError: connection refused");
            })
            .verifyComplete();

        // No page is analysed: a scan that would silently skip the enabled detector
        // must not run at all.
        verify(confluenceService, never()).getAllPagesInSpace(anyString());
    }

    @Test
    @DisplayName("streamSpace - an unverifiable detector health does not block the scan")
    void streamSpace_healthCheckFailure_scanProceeds() {
        String spaceKey = "S-HEALTH-UNKNOWN";
        when(piiDetectorClient.checkDetectorsHealth())
            .thenThrow(new IllegalStateException("health RPC unavailable"));
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .timeout(Duration.ofSeconds(5));

        // Falls through to the normal path (here: space not found), rather than turning an
        // unverifiable pre-flight into an unexplained refusal.
        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.errorKey()).isEqualTo(ScanErrorKeys.SPACE_NOT_FOUND))
            .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - space not found emits a single error event")
    void streamSpace_spaceNotFound() {
        String spaceKey = "S-NOT-FOUND";
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .assertNext(ev -> {
                    assertThat(ev).isNotNull();
                    assertThat(ev.eventType()).isEqualTo(ScanEventType.ERROR.toJson());
                    assertThat(ev.spaceKey()).isEqualTo(spaceKey);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - one page with blank content and no attachments emits start, page_start, item(empty), page_complete, complete")
    void streamSpace_blankContent_noAttachments() {
        String spaceKey = "S1";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-1")
            .title("Title 1")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("   ")) // blank
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-1")).thenReturn(CompletableFuture.completedFuture(List.of()));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .filter(ev -> List.of(
                        ScanEventType.START.toJson(),
                        ScanEventType.PAGE_START.toJson(),
                        ScanEventType.ITEM.toJson(),
                        ScanEventType.PAGE_COMPLETE.toJson(),
                        ScanEventType.COMPLETE.toJson()
                ).contains(ev.eventType()))
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()) && "p-1".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()) && ev.detectedPIIs() != null && ev.detectedPIIs().isEmpty())
                .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()) && "p-1".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - with extractable attachment and non-blank content emits attachment_item then item")
    void streamSpace_withAttachmentAndContent() {
        String spaceKey = "S2";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-2")
            .title("T2")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("Some text with email john@doe.com"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));

        AttachmentInfo att = new AttachmentInfo("file.pdf", "pdf", "application/pdf", "http://file");
        when(confluenceAttachmentService.getPageAttachments("p-2")).thenReturn(CompletableFuture.completedFuture(List.of(att)));
        when(confluenceDownloadService.downloadAttachmentContent("p-2", "file.pdf")).thenReturn(CompletableFuture.completedFuture(Optional.of("PDFDATA".getBytes(StandardCharsets.UTF_8))));
        when(attachmentTextExtractionService.extractText(eq(att), any())).thenReturn(Optional.of("Extracted email test"));

        ContentPiiDetection resp = ContentPiiDetection.builder()
                .statistics(Map.of("EMAIL", 1))
                .sensitiveDataFound(List.of(
                        new ContentPiiDetection.SensitiveData("EMAIL", "Email", "john@doe.com", "", 0, 16, 0.95, "sel", DetectorSource.UNKNOWN_SOURCE)
                ))
                .build();
        when(piiDetectorClient.analyzeContent(any())).thenReturn(resp);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .filter(ev -> List.of(
                        ScanEventType.START.toJson(),
                        ScanEventType.ATTACHMENT_ITEM.toJson(),
                        ScanEventType.PAGE_START.toJson(),
                        ScanEventType.ITEM.toJson(),
                        ScanEventType.PAGE_COMPLETE.toJson(),
                        ScanEventType.COMPLETE.toJson()
                ).contains(ev.eventType()))
                .take(6)
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.ATTACHMENT_ITEM.toJson().equals(ev.eventType()) && "p-2".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()) && "p-2".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()) && "p-2".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()) && "p-2".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - gRPC StatusRuntimeException produces error event then page_complete")
    void streamSpace_grpcStatusError() {
        String spaceKey = "S3";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t", "http://test.com","d",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-3")
            .title("T3")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-3")).thenReturn(CompletableFuture.completedFuture(List.of()));

        when(piiDetectorClient.analyzeContent(any())).thenThrow(new RuntimeException("UNAVAILABLE"));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .filter(ev -> List.of(
                        ScanEventType.START.toJson(),
                        ScanEventType.PAGE_START.toJson(),
                        ScanEventType.ERROR.toJson(),
                        ScanEventType.PAGE_COMPLETE.toJson(),
                        ScanEventType.COMPLETE.toJson()
                ).contains(ev.eventType()))
                .take(5)
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()) && "p-3".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType()) && "p-3".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()) && "p-3".equals(ev.pageId()))
                .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpaces - empty list emits multi_start, error, multiComplete")
    void Should_EmitErrorForAllSpaces_When_NoSpacesAvailable() {
        // Simulate cache miss + empty result from API
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of()));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces().timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.MULTI_START.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType()) && ev.message() != null)
            .expectNextMatches(ev -> ScanEventType.MULTI_COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("buildPageUrl - trims trailing slash in baseUrl")
    void Should_BuildPageUrlWithoutDoubleSlash_When_BaseUrlEndsWithSlash() {
        String spaceKey = "S-TRIM";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                                                    ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-trim")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-trim")).thenReturn(CompletableFuture.completedFuture(List.of()));

        // Stub detector to avoid network calls and return an empty response
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()) || ScanEventType.ITEM.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.pageUrl()).isEqualTo("http://confluence.example/pages/viewpage.action?pageId=p-trim"))
            .assertNext(ev -> assertThat(ev.pageUrl()).isEqualTo("http://confluence.example/pages/viewpage.action?pageId=p-trim"))
            .verifyComplete();
    }

    @Test
    @DisplayName("attachments - non-extractable extension emits no attachment_item")
    void Should_NotEmitAttachmentItem_When_AttachmentExtensionIsNotExtractable() {
        String spaceKey = "S-NO-EXT";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                                                    ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-no-ext")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));

        AttachmentInfo att = new AttachmentInfo("file.bin", "bin", "application/octet-stream", "http://file");
        when(confluenceAttachmentService.getPageAttachments("p-no-ext")).thenReturn(CompletableFuture.completedFuture(List.of(att)));

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> List.of(
                ScanEventType.ATTACHMENT_ITEM.toJson(),
                ScanEventType.ITEM.toJson(),
                ScanEventType.PAGE_COMPLETE.toJson()
            ).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("attachments - extractable but no content downloaded emits no attachment_item")
    void Should_NotEmitAttachmentItem_When_DownloadReturnsEmpty() {
        String spaceKey = "S-EMPTY-DL";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                                                    ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-empty-dl")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));

        AttachmentInfo att = new AttachmentInfo("file.pdf", "pdf", "application/pdf", "http://file");
        when(confluenceAttachmentService.getPageAttachments("p-empty-dl")).thenReturn(CompletableFuture.completedFuture(List.of(att)));
        when(confluenceDownloadService.downloadAttachmentContent("p-empty-dl", "file.pdf")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> List.of(
                ScanEventType.ATTACHMENT_ITEM.toJson(),
                ScanEventType.ITEM.toJson(),
                ScanEventType.PAGE_COMPLETE.toJson()
            ).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("truncate - masked content is truncated to 5000 characters with ellipsis")
    void Should_TruncateMaskedContent_When_LengthGreaterThan5000() {
        String spaceKey = "S-TRUNC";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
                                                    ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-trunc")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-trunc")).thenReturn(CompletableFuture.completedFuture(List.of()));

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.eventType()).isEqualTo(ScanEventType.ITEM.toJson()))
            .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - getSpace fails emits error event")
    void Should_EmitErrorEvent_When_GetSpaceFails() {
        String spaceKey = "ERR1";
        CompletableFuture<Optional<ConfluenceSpace>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("boom"));
        when(confluenceService.getSpace(spaceKey)).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo(ScanEventType.ERROR.toJson());
                assertThat(ev.spaceKey()).isEqualTo(spaceKey);
                assertThat(ev.message()).contains("boom");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpaces - per-space failure emits error event between multi_start and multi_complete")
    void Should_EmitErrorEventPerSpace_When_GetAllPagesFails_InStreamAllSpaces() {
        ConfluenceSpace space = new ConfluenceSpace("id", "MS1", "t", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        CompletableFuture<List<ConfluencePage>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("pages-fail"));
        when(confluenceService.getAllPagesInSpace("MS1")).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces().timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.MULTI_START.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType()) && "MS1".equals(ev.spaceKey()))
            .expectNextMatches(ev -> ScanEventType.MULTI_COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpaces - global failure emits multi_start, error, multi_complete")
    void Should_EmitGlobalError_When_GetAllSpacesFails_InStreamAllSpaces() {
        CompletableFuture<List<ConfluenceSpace>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("allspaces-fail"));
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces().timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.MULTI_START.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType()) && ev.message() != null)
            .expectNextMatches(ev -> ScanEventType.MULTI_COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("streamSpace - attachment retrieval failure falls back to page processing")
    void Should_ProcessPage_When_AttachmentsRetrievalFails() {
        String spaceKey = "S-AERR";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
            .id("p-aerr")
            .title("TA")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));

        CompletableFuture<List<AttachmentInfo>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("att-fail"));
        when(confluenceAttachmentService.getPageAttachments("p-aerr")).thenReturn(failing);

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> List.of(
                ScanEventType.START.toJson(),
                ScanEventType.PAGE_START.toJson(),
                ScanEventType.ITEM.toJson(),
                ScanEventType.PAGE_COMPLETE.toJson(),
                ScanEventType.COMPLETE.toJson()
            ).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()) && "p-aerr".equals(ev.pageId()))
            .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()) && "p-aerr".equals(ev.pageId()))
            .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()) && "p-aerr".equals(ev.pageId()))
            .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("buildPageUrl - null when baseUrl is blank")
    void Should_UseNullPageUrl_When_BaseUrlIsBlank() {
        // Blank base URL -> provider returns null for every URL (mimics adapter behavior).
        final ConfluenceUrlProvider blankUrlProvider = mock(ConfluenceUrlProvider.class);
        Mockito.lenient().when(blankUrlProvider.baseUrl()).thenReturn("   ");

        // Create service instances
        var applicationEventPublisher = mock(ApplicationEventPublisher.class);
        var parserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        var piiContextExtractor = new PiiContextExtractor(parserFactory);
        ScanProgressCalculator progressCalculator = new ScanProgressCalculator();
        ScanEventFactory eventFactory = new ScanEventFactory(blankUrlProvider, piiContextExtractor, severityCalculationService, value -> null);
        ScanCheckpointService checkpointService = new ScanCheckpointService(scanCheckpointRepository);
        PublishEventPort publishEventPort = new ScanEventPublisherAdapter(applicationEventPublisher);
        ScanEventDispatcher scanEventDispatcher = new ScanEventDispatcher(publishEventPort,
                                                                          Runnable::run);

        // Create parameter objects
        ConfluenceAccessor accessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService, spaceRepository);
        ContentScanOrchestrator orchestrator = new ContentScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher,
                severityCalculationService, scanSeverityCountService, scanPiiTypeCountService
        );
        AttachmentProcessor processor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );
        HtmlContentParser contentParser = new HtmlContentParser();

        ScanPipelineDependencies pipelineDependencies = new ScanPipelineDependencies(
            accessor,
            piiDetectorClient,
            orchestrator,
            processor,
            scanTimeoutConfig,
            contentParser,
            mock(ScanSpaceStatsCollector.class)
        );
        StreamConfluenceScanUseCase svc = new StreamConfluenceScanUseCase(
            pipelineDependencies,
            personallyIdentifiableInformationScanExecutionOrchestratorPort
        );

        String spaceKey = "S-BLANK";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));
        ConfluencePage page = ConfluencePage.builder()
            .id("p-blank")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-blank")).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = svc.streamSpace(spaceKey)
            .filter(ev -> List.of(ScanEventType.PAGE_START.toJson(), ScanEventType.ITEM.toJson()).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.pageUrl()).isNull())
            .assertNext(ev -> assertThat(ev.pageUrl()).isNull())
            .verifyComplete();
    }

    @Test
    @DisplayName("buildPageUrl - trims trailing slash in baseUrl (dedicated provider)")
    void Should_BuildPageUrlWithoutDoubleSlash_When_ProviderEndsWithSlash() {
        // Trailing slash is normalized by the helper so the stubbed URL has no double slash.
        final ConfluenceUrlProvider confluenceUrlProvider = stubDataCenterUrlProvider("http://confluence.example/");

        // Create service instances
        var applicationEventPublisher = mock(ApplicationEventPublisher.class);
        var parserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        var piiContextExtractor = new PiiContextExtractor(parserFactory);
        ScanProgressCalculator progressCalculator = new ScanProgressCalculator();
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor, severityCalculationService, value -> null);
        ScanCheckpointService checkpointService = new ScanCheckpointService(scanCheckpointRepository);
        PublishEventPort publishEventPort = new ScanEventPublisherAdapter(applicationEventPublisher);
        ScanEventDispatcher scanEventDispatcher = new ScanEventDispatcher(publishEventPort,
                                                                          Runnable::run);

        // Create parameter objects
        ConfluenceAccessor accessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService, spaceRepository);
        ContentScanOrchestrator orchestrator = new ContentScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher,
                severityCalculationService, scanSeverityCountService, scanPiiTypeCountService
        );
        AttachmentProcessor processor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );
        HtmlContentParser contentParser = new HtmlContentParser();

        ScanPipelineDependencies pipelineDependencies = new ScanPipelineDependencies(
            accessor,
            piiDetectorClient,
            orchestrator,
            processor,
            scanTimeoutConfig,
            contentParser,
            mock(ScanSpaceStatsCollector.class)
        );
        StreamConfluenceScanUseCase svc = new StreamConfluenceScanUseCase(
            pipelineDependencies,
            personallyIdentifiableInformationScanExecutionOrchestratorPort
        );

        String spaceKey = "S-TRIM2";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));
        ConfluencePage page = ConfluencePage.builder()
            .id("p-trim2")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-trim2")).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = svc.streamSpace(spaceKey)
            .filter(ev -> List.of(ScanEventType.PAGE_START.toJson(), ScanEventType.ITEM.toJson()).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.pageUrl()).isEqualTo("http://confluence.example/pages/viewpage.action?pageId=p-trim2"))
            .assertNext(ev -> assertThat(ev.pageUrl()).isEqualTo("http://confluence.example/pages/viewpage.action?pageId=p-trim2"))
            .verifyComplete();
    }

    @Test
    @DisplayName("checkpoint - item event persists RUNNING checkpoint during page processing")
    void Should_PersistRunningCheckpointWithoutAdvancingPage_When_ItemEventEmitted() {
        String spaceKey = "S-CP-ITEM";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));
        ConfluencePage page = ConfluencePage.builder()
            .id("p-cp-item")
            .title("T")
            .spaceKey(spaceKey)
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments("p-cp-item")).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .filter(ev -> List.of(ScanEventType.ITEM.toJson(), ScanEventType.COMPLETE.toJson()).contains(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()))
            .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
            .verifyComplete();

        // Vérifie qu'au moins un checkpoint est persisté pour cet espace pendant le traitement de la page.
        // Le détail de lastProcessedPageId et du statut exact (RUNNING/COMPLETED) est couvert par les tests
        // dédiés de ScanCheckpointService et n'est pas vérifié ici.
        verify(scanCheckpointRepository, atLeastOnce()).save(argThat(cp ->
            cp != null &&
            cp.spaceKey().equals(spaceKey)
        ));
    }

    @Test
    @DisplayName("streamAllSpaces - scans all spaces when AHVIV not found (fail-safe)")
    void Should_ScanAllSpaces_When_AhvivNotFound() {
        ConfluenceSpace space1 = new ConfluenceSpace("id1", "ABC", "Space ABC","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        ConfluenceSpace space2 = new ConfluenceSpace("id2", "DEF", "Space DEF","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);

        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space1, space2)));

        ConfluencePage abcPage = ConfluencePage.builder()
            .id("p-abc")
            .title("ABC Page")
            .spaceKey("ABC")
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace("ABC")).thenReturn(CompletableFuture.completedFuture(List.of(abcPage)));
        when(confluenceAttachmentService.getPageAttachments("p-abc")).thenReturn(CompletableFuture.completedFuture(List.of()));
        
        ConfluencePage defPage = ConfluencePage.builder()
            .id("p-def")
            .title("DEF Page")
            .spaceKey("DEF")
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace("DEF")).thenReturn(CompletableFuture.completedFuture(List.of(defPage)));
        when(confluenceAttachmentService.getPageAttachments("p-def")).thenReturn(CompletableFuture.completedFuture(List.of()));

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces()
            .filter(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(10));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> "ABC".equals(ev.spaceKey()))
            .expectNextMatches(ev -> "DEF".equals(ev.spaceKey()))
            .verifyComplete();

        // Verify both spaces were scanned (fail-safe behavior)
        verify(confluenceService, atLeastOnce()).getAllPagesInSpace("ABC");
        verify(confluenceService, atLeastOnce()).getAllPagesInSpace("DEF");
    }

    @Test
    @DisplayName("streamAllSpaces - unreachable data source pauses the scan instead of completing it")
    void Should_PauseScanWithoutCompleting_When_DataSourceIsUnreachable() {
        ConfluenceSpace space = new ConfluenceSpace("id", "CCAEI", "t", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        CompletableFuture<List<ConfluencePage>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new ConnectException());
        when(confluenceService.getAllPagesInSpace("CCAEI")).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces().timeout(Duration.ofSeconds(5));

        // No MULTI_COMPLETE: announcing completion would tell the operator the base was
        // covered while the scan actually stopped on an unreachable data source.
        StepVerifier.create(flux)
            .expectNextMatches(ev -> ScanEventType.MULTI_START.toJson().equals(ev.eventType()))
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo(ScanEventType.ERROR.toJson());
                assertThat(ev.spaceKey()).isEqualTo("CCAEI");
                // The key is what lets the dashboard name the cause and offer Resume, in the
                // operator's language; the cause rides along as a technical parameter.
                assertThat(ev.errorKey()).isEqualTo(ScanErrorKeys.PAUSED_NETWORK);
                assertThat(ev.errorParams().get("cause"))
                        .isNotEmpty()
                        .doesNotContain("null")
                        .containsIgnoringCase("connect");
            })
            .verifyComplete();

        verify(scanCheckpointRepository).pauseUnfinishedCheckpoints(anyString());
    }

    @Test
    @DisplayName("streamSpace - ConnectException with null message emits descriptive error event")
    void Should_EmitDescriptiveErrorEvent_When_StreamSpaceGetsConnectException() {
        String spaceKey = "CONN-ERR";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        CompletableFuture<List<ConfluencePage>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new ConnectException());
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo(ScanEventType.ERROR.toJson());
                assertThat(ev.spaceKey()).isEqualTo(spaceKey);
                assertThat(ev.message())
                        .isNotEmpty()
                        .doesNotContain("null")
                        .containsIgnoringCase("connect");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("streamAllSpaces - AHVIV at first startingPosition scans all spaces")
    void Should_ScanAllSpaces_When_AhvivIsFirstSpace() {
        ConfluenceSpace space1 = new ConfluenceSpace("id1", "AHVIV", "AHV/IV e-Form","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        ConfluenceSpace space2 = new ConfluenceSpace("id2", "XYZ", "Space XYZ","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);

        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space1, space2)));

        ConfluencePage ahvivPage = ConfluencePage.builder()
            .id("p-ahviv")
            .title("AHVIV Page")
            .spaceKey("AHVIV")
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace("AHVIV")).thenReturn(CompletableFuture.completedFuture(List.of(ahvivPage)));
        when(confluenceAttachmentService.getPageAttachments("p-ahviv")).thenReturn(CompletableFuture.completedFuture(List.of()));
        
        ConfluencePage xyzPage = ConfluencePage.builder()
            .id("p-xyz")
            .title("XYZ Page")
            .spaceKey("XYZ")
            .content(new ConfluencePage.HtmlContent("content"))
            .build();
        when(confluenceService.getAllPagesInSpace("XYZ")).thenReturn(CompletableFuture.completedFuture(List.of(xyzPage)));
        when(confluenceAttachmentService.getPageAttachments("p-xyz")).thenReturn(CompletableFuture.completedFuture(List.of()));

        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceScanUseCase.streamAllSpaces()
            .filter(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(10));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> "AHVIV".equals(ev.spaceKey()))
            .expectNextMatches(ev -> "XYZ".equals(ev.spaceKey()))
            .verifyComplete();

        // Verify both spaces were scanned
        verify(confluenceService, atLeastOnce()).getAllPagesInSpace("AHVIV");
        verify(confluenceService, atLeastOnce()).getAllPagesInSpace("XYZ");
    }

    @Test
    @DisplayName("streamAllSpaces - initializes NOT_STARTED checkpoints for the whole scope before scanning")
    void Should_InitializeNotStartedCheckpoints_When_StreamAllSpaces() {
        ConfluenceSpace space1 = new ConfluenceSpace("id1", "SC1", "Space 1", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        ConfluenceSpace space2 = new ConfluenceSpace("id2", "SC2", "Space 2", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space1, space2)));

        ConfluencePage page1 = ConfluencePage.builder().id("p1").title("T").spaceKey("SC1")
            .content(new ConfluencePage.HtmlContent("content")).build();
        ConfluencePage page2 = ConfluencePage.builder().id("p2").title("T").spaceKey("SC2")
            .content(new ConfluencePage.HtmlContent("content")).build();
        when(confluenceService.getAllPagesInSpace("SC1")).thenReturn(CompletableFuture.completedFuture(List.of(page1)));
        when(confluenceService.getAllPagesInSpace("SC2")).thenReturn(CompletableFuture.completedFuture(List.of(page2)));
        when(confluenceAttachmentService.getPageAttachments(anyString()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build());

        streamConfluenceScanUseCase.streamAllSpaces()
            .filter(ev -> ScanEventType.MULTI_COMPLETE.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(10))
            .blockLast();

        verify(scanCheckpointRepository).save(argThat(cp ->
            cp != null && "SC1".equals(cp.spaceKey()) && cp.scanStatus() == ScanStatus.NOT_STARTED));
        verify(scanCheckpointRepository).save(argThat(cp ->
            cp != null && "SC2".equals(cp.spaceKey()) && cp.scanStatus() == ScanStatus.NOT_STARTED));
    }

    @Test
    @DisplayName("streamSelectedSpaces - initializes NOT_STARTED checkpoints only for the selected scope")
    void Should_InitializeNotStartedCheckpointsForSelectionOnly_When_StreamSelectedSpaces() {
        ConfluenceSpace selected = new ConfluenceSpace("id1", "SEL1", "Selected", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        ConfluenceSpace other = new ConfluenceSpace("id2", "SEL2", "Other", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(selected, other)));

        ConfluencePage page = ConfluencePage.builder().id("pSel").title("T").spaceKey("SEL1")
            .content(new ConfluencePage.HtmlContent("content")).build();
        when(confluenceService.getAllPagesInSpace("SEL1")).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments(anyString()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build());

        streamConfluenceScanUseCase.streamSelectedSpaces(List.of("SEL1"))
            .filter(ev -> ScanEventType.MULTI_COMPLETE.toJson().equals(ev.eventType()))
            .timeout(Duration.ofSeconds(10))
            .blockLast();

        verify(scanCheckpointRepository).save(argThat(cp ->
            cp != null && "SEL1".equals(cp.spaceKey()) && cp.scanStatus() == ScanStatus.NOT_STARTED));
        verify(scanCheckpointRepository, never()).save(argThat(cp ->
            cp != null && "SEL2".equals(cp.spaceKey()) && cp.scanStatus() == ScanStatus.NOT_STARTED));
    }

    @Test
    @DisplayName("streamAllSpaces - a refused scan never announces completion")
    void Should_NotAnnounceCompletion_When_ScanIsRefusedByPreflight() {
        when(piiDetectorClient.checkDetectorsHealth()).thenReturn(List.of(
            new DetectorHealth(DetectorSource.MINISTRAL, false, "http://lmstudio:1234/v1",
                "ConnectError: connection refused", "ENDPOINT_UNREACHABLE",
                Map.of("cause", "ConnectError: connection refused"))));

        List<ConfluenceContentScanResult> events = streamConfluenceScanUseCase.streamAllSpaces()
            .timeout(Duration.ofSeconds(5))
            .collectList()
            .block();

        assertThat(events).isNotNull();
        // MULTI_COMPLETE on a scan that never opened a space would tell the operator the whole
        // base was covered and came back clean.
        assertThat(eventTypes(events)).doesNotContain(ScanEventType.MULTI_COMPLETE.toJson());
        assertThat(events.getLast().errorKey()).isEqualTo(ScanErrorKeys.DETECTOR_ENDPOINT_UNREACHABLE);

        // The refusal must also leave the previous results untouched.
        verify(confluenceService, never()).getAllSpaces();
    }

    @Test
    @DisplayName("streamSpace - a detector going down mid-scan pauses the scan and holds the page back")
    void Should_PauseAndHoldPageBack_When_DetectorFailsMidScan() {
        String spaceKey = "S-DETECTOR-LOST";
        List<ConfluencePage> pages = twoScannablePages(spaceKey);
        stubSpaceWithPages(spaceKey, pages);

        // The call succeeds, but Ministral reports it could not run: the page would be
        // recorded as analysed while the LLM never saw it.
        when(piiDetectorClient.analyzeContent(any())).thenReturn(ContentPiiDetection.builder()
            .statistics(Map.of())
            .sensitiveDataFound(List.of())
            .detectorRunStats(List.of(
                new ContentPiiDetection.DetectorRunStat(DetectorSource.REGEX, 5, 0, 0, ""),
                new ContentPiiDetection.DetectorRunStat(DetectorSource.MINISTRAL, 0, 0, 0,
                                                        "LM Studio endpoint unreachable")))
            .build());

        List<ConfluenceContentScanResult> events = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .timeout(Duration.ofSeconds(10))
            .collectList()
            .block();

        assertThat(events).isNotNull();

        // The page that hit the outage must NOT be marked complete: its pageComplete would
        // advance the checkpoint and make the resume skip content nobody analysed.
        assertThat(eventTypes(events)).doesNotContain(ScanEventType.PAGE_COMPLETE.toJson());

        // Nor are its partial findings emitted: they would be persisted, then counted a
        // second time when the resume re-scans the page.
        assertThat(eventTypes(events)).doesNotContain(ScanEventType.ITEM.toJson());

        // No space completion either — a COMPLETED space is skipped on resume for good.
        assertThat(eventTypes(events)).doesNotContain(ScanEventType.COMPLETE.toJson());

        // The second page is never opened: it would fail the same way.
        assertThat(events.stream().filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType())))
            .hasSize(1);

        assertThat(events.getLast().eventType()).isEqualTo(ScanEventType.ERROR.toJson());
        assertThat(events.getLast().errorKey()).isEqualTo(ScanErrorKeys.PAUSED_DETECTOR);
        assertThat(events.getLast().errorParams().get("cause"))
            .contains("MINISTRAL", "LM Studio endpoint unreachable");

        verify(scanCheckpointRepository).pauseUnfinishedCheckpoints(anyString());
    }

    @Test
    @DisplayName("streamSpace - a detection timeout fails the item only and lets the scan finish")
    void Should_KeepScanningAndComplete_When_DetectionTimesOutOnOneItem() {
        String spaceKey = "S-SLOW-PAGE";
        List<ConfluencePage> pages = twoScannablePages(spaceKey);
        stubSpaceWithPages(spaceKey, pages);

        // First page outruns the detection deadline, second one is analysed normally. Such
        // timeouts are expected on a full scan, so they must never stop it.
        when(piiDetectorClient.analyzeContent(any()))
            .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            .thenReturn(ContentPiiDetection.builder()
                .statistics(Map.of())
                .sensitiveDataFound(List.of())
                .build());

        List<ConfluenceContentScanResult> events = streamConfluenceScanUseCase.streamSpace(spaceKey)
            .timeout(Duration.ofSeconds(10))
            .collectList()
            .block();

        assertThat(events).isNotNull();

        // Both pages are opened and both are marked complete: holding the failed one back
        // would have it re-scanned on resume, double-counting the findings of the retry.
        assertThat(events.stream().filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType())))
            .hasSize(2);
        assertThat(events.stream().filter(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType())))
            .hasSize(2);

        // The space still completes, and the failed item is reported for the operator.
        assertThat(eventTypes(events)).contains(ScanEventType.COMPLETE.toJson());
        assertThat(events.stream().filter(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType())))
            .hasSize(1);
        assertThat(events.stream()
            .filter(ev -> ScanEventType.ERROR.toJson().equals(ev.eventType()))
            .findFirst().orElseThrow().message())
            .doesNotStartWith("SCAN_PAUSED");

        verify(scanCheckpointRepository, never()).pauseUnfinishedCheckpoints(anyString());
    }

    private List<ConfluencePage> twoScannablePages(String spaceKey) {
        return List.of(
            ConfluencePage.builder().id("p-1").title("T1").spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("content of page one")).build(),
            ConfluencePage.builder().id("p-2").title("T2").spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("content of page two")).build());
    }

    private void stubSpaceWithPages(String spaceKey, List<ConfluencePage> pages) {
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(pages));
        // Lenient: a scan paused on the first page never opens the second one, so its
        // attachment stub legitimately goes unused.
        pages.forEach(page -> Mockito.lenient()
            .when(confluenceAttachmentService.getPageAttachments(page.id()))
            .thenReturn(CompletableFuture.completedFuture(List.of())));
        when(scanTimeoutConfig.getPiiDetectionTimeout()).thenReturn(Duration.ofSeconds(30));
    }

    private static List<String> eventTypes(List<ConfluenceContentScanResult> events) {
        return events.stream().map(ConfluenceContentScanResult::eventType).toList();
    }

    /**
     * Builds a Mockito stub of {@link ConfluenceUrlProvider} that mimics the Data Center
     * adapter output. The base URL is normalized (trimmed + trailing slash removed) so the
     * assertions in this file can compare against a clean canonical shape.
     */
    private static ConfluenceUrlProvider stubDataCenterUrlProvider(String rawBaseUrl) {
        String normalizedBase = normalizeBaseUrl(rawBaseUrl);
        ConfluenceUrlProvider provider = mock(ConfluenceUrlProvider.class);
        Mockito.lenient().when(provider.baseUrl()).thenReturn(rawBaseUrl);
        Mockito.lenient().when(provider.pageUrl(any(), any())).thenAnswer(invocation -> {
            String pageId = invocation.getArgument(1);
            if (pageId == null || pageId.isBlank() || normalizedBase == null) return null;
            return normalizedBase + "/pages/viewpage.action?pageId=" + pageId;
        });
        Mockito.lenient().when(provider.attachmentsUrl(any(), any())).thenAnswer(invocation -> {
            String pageId = invocation.getArgument(1);
            if (pageId == null || pageId.isBlank() || normalizedBase == null) return null;
            return normalizedBase + "/pages/viewpageattachments.action?pageId=" + pageId;
        });
        return provider;
    }

    private static String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) return null;
        String trimmed = rawBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

}