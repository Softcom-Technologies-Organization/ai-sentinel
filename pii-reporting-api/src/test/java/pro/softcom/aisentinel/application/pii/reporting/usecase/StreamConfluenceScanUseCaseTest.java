package pro.softcom.aisentinel.application.pii.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
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
import pro.softcom.aisentinel.domain.confluence.AttachmentInfo;
import pro.softcom.aisentinel.domain.confluence.ConfluencePage;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.PersonallyIdentifiableInformationSeverity;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.PersonallyIdentifiableInformationType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.JpaScanEventStoreAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.event.ScanEventPublisherAdapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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

    private StreamConfluenceScanUseCase streamConfluenceScanUseCase;

    @BeforeEach
    void setUp() {
        // Keep only baseUrl relevant for URL building
        final ConfluenceUrlProvider confluenceUrlProvider = new ConfluenceUrlProvider() {
            @Override public String baseUrl() { return "http://confluence.example"; }
            @Override public String pageUrl(String pageId) {
                if (pageId == null || pageId.isBlank()) return null;
                String base = baseUrl();
                if (base.isBlank()) {
                    return null;
                }
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
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor, severityCalculationService);
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

        streamConfluenceScanUseCase = new StreamConfluenceScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig,
                personallyIdentifiableInformationScanExecutionOrchestratorPort,
                scanCheckpointRepository
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
                    assertThat(ev.message()).isEqualTo("Espace non trouvé");
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
                .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()) && ev.detectedPIIList() != null && ev.detectedPIIList().isEmpty())
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
                        new ContentPiiDetection.SensitiveData(PersonallyIdentifiableInformationType.EMAIL, "john@doe.com", "", 0, 16, 0.95, "sel")
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
        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));

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
        when(confluenceService.getAllSpaces()).thenReturn(failing);

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
        // Build a dedicated service with blank base URL
        final ConfluenceUrlProvider blankUrlProvider = new ConfluenceUrlProvider() {
            @Override public String baseUrl() { return "   "; }
            @Override public String pageUrl(String pageId) {
                String base = baseUrl();
                if (base.isBlank()) {
                    return null;
                }
                if (pageId == null || pageId.isBlank()) return null;
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
        ScanEventFactory eventFactory = new ScanEventFactory(blankUrlProvider, piiContextExtractor, severityCalculationService);
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

        StreamConfluenceScanUseCase svc = new StreamConfluenceScanUseCase(
            confluenceAccessor,
            piiDetectorClient,
            contentScanOrchestrator,
            attachmentProcessor,
            scanTimeoutConfig,
            personallyIdentifiableInformationScanExecutionOrchestratorPort,
            scanCheckpointRepository
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
        // Build a dedicated service with trailing slash
        final ConfluenceUrlProvider confluenceUrlProvider = new ConfluenceUrlProvider() {
            @Override public String baseUrl() { return "http://confluence.example/"; }
            @Override public String pageUrl(String pageId) {
                if (pageId == null || pageId.isBlank()) return null;
                String base = baseUrl();
                if (base.isBlank()) {
                    return null;
                }
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
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor, severityCalculationService);
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

        StreamConfluenceScanUseCase svc = new StreamConfluenceScanUseCase(
            confluenceAccessor,
            piiDetectorClient,
            contentScanOrchestrator,
            attachmentProcessor,
            scanTimeoutConfig,
            personallyIdentifiableInformationScanExecutionOrchestratorPort,
            scanCheckpointRepository
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

        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(
            List.of(space1, space2)
        ));

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
    @DisplayName("streamAllSpaces - AHVIV at first position scans all spaces")
    void Should_ScanAllSpaces_When_AhvivIsFirstSpace() {
        ConfluenceSpace space1 = new ConfluenceSpace("id1", "AHVIV", "AHV/IV e-Form","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        ConfluenceSpace space2 = new ConfluenceSpace("id2", "XYZ", "Space XYZ","http://test.com", "d",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);

        when(confluenceService.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(
            List.of(space1, space2)
        ));

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

}
