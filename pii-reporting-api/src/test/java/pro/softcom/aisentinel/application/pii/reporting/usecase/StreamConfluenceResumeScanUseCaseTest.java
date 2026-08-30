package pro.softcom.aisentinel.application.pii.reporting.usecase;

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
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.*;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.JpaScanEventStoreAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.event.ScanEventPublisherAdapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamConfluenceResumeScanUseCaseTest {

    @Mock
    private ConfluenceClient confluenceService;

    @Mock
    private ConfluenceAttachmentDownloader confluenceDownloadService;

    @Mock
    private ConfluenceAttachmentClient confluenceAttachmentService;

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
    private pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService severityCalculationService;

    @Mock
    private pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService scanSeverityCountService;

    @Mock
    private pro.softcom.aisentinel.application.pii.reporting.ScanPiiTypeCountService scanPiiTypeCountService;



    @Mock
    private ConfluenceSpaceRepository spaceRepository;

    @Mock
    private PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestratorPort;

    private StreamConfluenceResumeScanPort streamConfluenceResumeScanPort;

    @BeforeEach
    void setUp() {
        final ConfluenceUrlProvider confluenceUrlProvider = stubDataCenterUrlProvider();
        
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
        ConfluenceAccessor confluenceAccessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService, spaceRepository);
        ContentScanOrchestrator contentScanOrchestrator = new ContentScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher,
                severityCalculationService, scanSeverityCountService, scanPiiTypeCountService
        );
        AttachmentProcessor attachmentProcessor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );
        HtmlContentParser htmlContentParser = new HtmlContentParser();
        ScanSpaceStatsCollector scanSpaceStatsCollector = mock(ScanSpaceStatsCollector.class);
        ScanPipelineDependencies pipelineDependencies = new ScanPipelineDependencies(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig,
                htmlContentParser,
                scanSpaceStatsCollector
        );
        streamConfluenceResumeScanPort = new StreamConfluenceResumeScanUseCase(
                pipelineDependencies,
                scanCheckpointRepository,
                scanExecutionOrchestratorPort
        );
    }

    private static ScanCheckpoint scopeCheckpoint(String scanId, String spaceKey) {
        return ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .scanStatus(ScanStatus.PAUSED)
            .build();
    }

    private static ConfluenceSpace space(String key) {
        return new ConfluenceSpace("id-" + key, key, "t", "http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
    }

    @Test
    @DisplayName("resumeAllSpaces - live scan re-attaches via subscribeScan without launching new work")
    void Should_AttachToLiveScan_When_ScanIsActive() {
        String scanId = "SID-LIVE";
        ConfluenceContentScanResult replayed = ConfluenceContentScanResult.builder()
            .scanId(scanId).eventType("item").emittedAt("t").build();
        when(scanExecutionOrchestratorPort.isScanActive(scanId)).thenReturn(true);
        when(scanExecutionOrchestratorPort.subscribeScan(scanId)).thenReturn(Flux.just(replayed));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId);

        StepVerifier.create(flux)
            .expectNext(replayed)
            .verifyComplete();

        verify(scanExecutionOrchestratorPort).subscribeScan(scanId);
        verify(confluenceService, never()).getAllSpaces();
        verify(scanCheckpointRepository, never()).findByScan(anyString());
        verify(scanCheckpointRepository, never()).resumeAllPausedCheckpoints(anyString());
    }

    @Test
    @DisplayName("resumeAllSpaces - inactive scan resumes only the spaces of its own persisted scope")
    void Should_ResumeOnlyScopedSpaces_When_ScanInactive() {
        String scanId = "SID-SCOPE";
        ConfluenceSpace inScope = space("SPACE_A");
        ConfluenceSpace outOfScope = space("SPACE_B");
        when(spaceRepository.findAll()).thenReturn(List.of());
        when(confluenceService.getAllSpaces())
            .thenReturn(CompletableFuture.completedFuture(List.of(inScope, outOfScope)));
        when(scanCheckpointRepository.findByScan(scanId))
            .thenReturn(List.of(scopeCheckpoint(scanId, "SPACE_A")));
        when(scanCheckpointRepository.findByScanAndSpace(scanId, "SPACE_A")).thenReturn(Optional.empty());

        ConfluencePage page = ConfluencePage.builder().id("pA").title("A").spaceKey("SPACE_A")
            .content(new ConfluencePage.HtmlContent("content")).build();
        when(confluenceService.getAllPagesInSpace("SPACE_A"))
            .thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments(anyString()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build());

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .filter(ev -> "start".equals(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.spaceKey()).isEqualTo("SPACE_A"))
            .verifyComplete();

        verify(confluenceService, never()).getAllPagesInSpace("SPACE_B");
    }

    @Test
    @DisplayName("resumeAllSpaces - empty persisted scope yields an empty flux and never scans")
    void Should_ReturnEmptyFlux_When_ScanHasNoCheckpoints() {
        String scanId = "SID-EMPTY";
        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of());

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux).verifyComplete();

        verify(confluenceService, never()).getAllSpaces();
    }

    @Test
    @DisplayName("resumeAllSpaces - attachment in progress decrements analyzedOffset")
    void Should_StartAtZeroProgress_When_AttachmentWasInProgress_OnResume() {
        String scanId = "SID-1";
        String spaceKey = "RS1";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        ScanCheckpoint cp = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .lastProcessedPageId("p1")
            .lastProcessedAttachmentName("att.bin")
            .scanStatus(ScanStatus.RUNNING)
            .build();
        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of(cp));
        when(scanCheckpointRepository.findByScanAndSpace(scanId, spaceKey)).thenReturn(Optional.of(cp));

        ConfluencePage p1 = ConfluencePage.builder().id("p1").title("P1").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("content"))
            .build();
        ConfluencePage p2 = ConfluencePage.builder().id("p2").title("P2").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("content2"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(p1, p2)));
        when(confluenceAttachmentService.getPageAttachments(anyString())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .filter(ev -> "start".equals(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.analysisProgressPercentage()).isZero())
            .verifyComplete();
    }

    @Test
    @DisplayName("resumeAllSpaces - per-space failure when getting pages emits error")
    void Should_EmitErrorEventPerSpace_When_GetAllPagesFails_OnResume() {
        String scanId = "SID-2";
        String spaceKey = "RS2";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of(scopeCheckpoint(scanId, spaceKey)));
        when(scanCheckpointRepository.findByScanAndSpace(scanId, spaceKey)).thenReturn(Optional.empty());

        CompletableFuture<List<ConfluencePage>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("resume-pages-fail"));
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(failing);

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo("scanError");
                assertThat(ev.spaceKey()).isEqualTo(spaceKey);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("resumeAllSpaces - preparation throws emits error per space")
    void Should_EmitErrorEventPerSpace_When_PreparationThrows_OnResume() {
        String scanId = "SID-3";
        String spaceKey = "RS3";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of(scopeCheckpoint(scanId, spaceKey)));
        when(scanCheckpointRepository.findByScanAndSpace(anyString(), anyString())).thenThrow(new RuntimeException("prep-fail"));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> {
                assertThat(ev.eventType()).isEqualTo("scanError");
                assertThat(ev.spaceKey()).isEqualTo(spaceKey);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("resumeAllSpaces - global failure when getting spaces emits error")
    void Should_EmitGlobalError_When_GetAllSpacesFails_OnResume() {
        String scanId = "SID-4";
        CompletableFuture<List<ConfluenceSpace>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("resume-allspaces-fail"));
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(failing);
        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of(scopeCheckpoint(scanId, "RS-any")));

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.eventType()).isEqualTo("scanError"))
            .verifyComplete();
    }

    @Test
    @DisplayName("resumeAllSpaces - unknown lastProcessedPageId resumes from beginning (indexOfPage -1)")
    void Should_ResumeFromUnknownPageId_When_CheckpointPageNotFound() {
        String scanId = "SID-5";
        String spaceKey = "RS4";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(spaceRepository.findAll()).thenReturn(List.of()); when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        ScanCheckpoint cp = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .lastProcessedPageId("UNKNOWN")
            .scanStatus(ScanStatus.RUNNING)
            .build();
        when(scanCheckpointRepository.findByScan(scanId)).thenReturn(List.of(cp));
        when(scanCheckpointRepository.findByScanAndSpace(scanId, spaceKey)).thenReturn(Optional.of(cp));

        ConfluencePage p1 = ConfluencePage.builder().id("pA").title("A").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("contentA"))
            .build();
        ConfluencePage p2 = ConfluencePage.builder().id("pB").title("B").spaceKey(spaceKey).content(new ConfluencePage.HtmlContent("contentB"))
            .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(p1, p2)));
        when(confluenceAttachmentService.getPageAttachments(anyString())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(piiDetectorClient.analyzeContent(any())).thenReturn(
            ContentPiiDetection.builder().sensitiveDataFound(List.of()).statistics(Map.of()).build()
        );

        Flux<ConfluenceContentScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()))
            .take(2)
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> "pA".equals(ev.pageId()))
            .expectNextMatches(ev -> "pB".equals(ev.pageId()))
            .verifyComplete();
    }

    /**
     * Builds a Mockito stub of {@link ConfluenceUrlProvider} that mimics the Data Center
     * adapter output, so test assertions can rely on a stable canonical URL shape.
     */
    private static ConfluenceUrlProvider stubDataCenterUrlProvider() {
        String normalizedBase = normalizeBaseUrl();
        ConfluenceUrlProvider provider = mock(ConfluenceUrlProvider.class);
        Mockito.lenient().when(provider.baseUrl()).thenReturn("http://confluence.example");
        Mockito.lenient().when(provider.pageUrl(any(), any())).thenAnswer(invocation -> {
            String pageId = invocation.getArgument(1);
            if (pageId == null || pageId.isBlank()) return null;
            return normalizedBase + "/pages/viewpage.action?pageId=" + pageId;
        });
        Mockito.lenient().when(provider.attachmentsUrl(any(), any())).thenAnswer(invocation -> {
            String pageId = invocation.getArgument(1);
            if (pageId == null || pageId.isBlank()) return null;
            return normalizedBase + "/pages/viewpageattachments.action?pageId=" + pageId;
        });
        return provider;
    }

    private static String normalizeBaseUrl() {
        return "http://confluence.example".trim();
    }
}
