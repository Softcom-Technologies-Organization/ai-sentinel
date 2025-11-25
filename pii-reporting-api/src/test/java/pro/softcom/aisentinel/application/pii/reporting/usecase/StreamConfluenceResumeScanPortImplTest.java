package pro.softcom.aisentinel.application.pii.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pro.softcom.aisentinel.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanEventDispatcher;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.JpaScanEventStoreAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.event.ScanEventPublisherAdapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StreamConfluenceResumeScanPortImplTest {

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

    private StreamConfluenceResumeScanPort streamConfluenceResumeScanPort;

    @BeforeEach
    void setUp() {
        final ConfluenceUrlProvider confluenceUrlProvider = new ConfluenceUrlProvider() {
            @Override public String baseUrl() { return "http://confluence.example"; }
            @Override public String pageUrl(String pageId) {
                if (pageId == null || pageId.isBlank()) return null;
                String base = baseUrl();
                if (base.isBlank()) return null;
                base = base.trim();
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                return base + "/pages/viewpage.action?pageId=" + pageId;
            }
        };
        
        // Create service instances
        var applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        var parserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        var piiContextExtractor = new PiiContextExtractor(parserFactory);
        ScanProgressCalculator progressCalculator = new ScanProgressCalculator();
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor);
        ScanCheckpointService checkpointService = new ScanCheckpointService(scanCheckpointRepository);
        PublishEventPort publishEventPort = new ScanEventPublisherAdapter(applicationEventPublisher);
        ScanEventDispatcher scanEventDispatcher = new ScanEventDispatcher(publishEventPort,
                                                                          Runnable::run);

        // Create parameter objects
        ConfluenceAccessor confluenceAccessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService);
        ContentScanOrchestrator contentScanOrchestrator = new ContentScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher,
                severityCalculationService, scanSeverityCountService
        );
        AttachmentProcessor attachmentProcessor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );
        
        streamConfluenceResumeScanPort = new StreamConfluenceResumeScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanCheckpointRepository,
                scanTimeoutConfig
        );
    }

    @Test
    @DisplayName("resumeAllSpaces - attachment in progress decrements analyzedOffset")
    void Should_StartAtZeroProgress_When_AttachmentWasInProgress_OnResume() {
        String scanId = "SID-1";
        String spaceKey = "RS1";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        ScanCheckpoint cp = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .lastProcessedPageId("p1")
            .lastProcessedAttachmentName("att.bin")
            .scanStatus(ScanStatus.RUNNING)
            .build();
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

        Flux<ScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .filter(ev -> "start".equals(ev.eventType()))
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .assertNext(ev -> assertThat(ev.analysisProgressPercentage()).isEqualTo(0.0))
            .verifyComplete();
    }

    @Test
    @DisplayName("resumeAllSpaces - per-space failure when getting pages emits error")
    void Should_EmitErrorEventPerSpace_When_GetAllPagesFails_OnResume() {
        String scanId = "SID-2";
        String spaceKey = "RS2";
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "t","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(scanCheckpointRepository.findByScanAndSpace(scanId, spaceKey)).thenReturn(Optional.empty());

        CompletableFuture<List<ConfluencePage>> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("resume-pages-fail"));
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(failing);

        Flux<ScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

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
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        when(scanCheckpointRepository.findByScanAndSpace(anyString(), anyString())).thenThrow(new RuntimeException("prep-fail"));

        Flux<ScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

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
        when(confluenceService.getAllSpaces()).thenReturn(failing);

        Flux<ScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId).timeout(Duration.ofSeconds(5));

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
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

        ScanCheckpoint cp = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .lastProcessedPageId("UNKNOWN")
            .scanStatus(ScanStatus.RUNNING)
            .build();
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

        Flux<ScanResult> flux = streamConfluenceResumeScanPort.resumeAllSpaces(scanId)
            .filter(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()))
            .take(2)
            .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
            .expectNextMatches(ev -> "pA".equals(ev.pageId()))
            .expectNextMatches(ev -> "pB".equals(ev.pageId()))
            .verifyComplete();
    }
}
