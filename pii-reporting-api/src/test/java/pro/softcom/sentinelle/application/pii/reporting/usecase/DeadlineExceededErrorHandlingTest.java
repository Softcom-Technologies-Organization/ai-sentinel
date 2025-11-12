package pro.softcom.sentinelle.application.pii.reporting.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventDispatcher;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.DataOwners;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.JpaScanEventStoreAdapter;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.event.ScanEventPublisherAdapter;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.PiiDetectionException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Test to reproduce and verify handling of DEADLINE_EXCEEDED errors wrapped in PiiDetectionException.
 * Business rule: When gRPC DEADLINE_EXCEEDED occurs, the scan must:
 * 1. Log the error with full context (spaceKey, pageId/pageTitle, attachmentName)
 * 2. Continue the Webflux stream without interruption
 * 3. Emit an error event to the frontend
 */
@ExtendWith(MockitoExtension.class)
class DeadlineExceededErrorHandlingTest {

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

    private StreamConfluenceScanUseCaseImpl streamConfluenceScanUseCase;

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

        var applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        var parserFactory = new ContentParserFactory(new PlainTextParser(), new HtmlContentParser());
        var piiContextExtractor = new PiiContextExtractor(parserFactory);
        ScanProgressCalculator progressCalculator = new ScanProgressCalculator();
        ScanEventFactory eventFactory = new ScanEventFactory(confluenceUrlProvider, piiContextExtractor);
        ScanCheckpointService checkpointService = new ScanCheckpointService(scanCheckpointRepository);
        PublishEventPort publishEventPort = new ScanEventPublisherAdapter(applicationEventPublisher);
        ScanEventDispatcher scanEventDispatcher = new ScanEventDispatcher(publishEventPort, Runnable::run);

        ConfluenceAccessor confluenceAccessor = new ConfluenceAccessor(confluenceService, confluenceAttachmentService);
        ScanOrchestrator scanOrchestrator = new ScanOrchestrator(
                eventFactory, progressCalculator, checkpointService, jpaScanEventStoreAdapter, scanEventDispatcher
        );
        AttachmentProcessor attachmentProcessor = new AttachmentProcessor(
                confluenceDownloadService,
                attachmentTextExtractionService
        );

        streamConfluenceScanUseCase = new StreamConfluenceScanUseCaseImpl(
                confluenceAccessor,
                piiDetectorClient,
                scanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig
        );
    }

    @Test
    @DisplayName("Should_LogAndContinueFlux_When_DeadlineExceededWrappedInPiiDetectionException_ForAttachment")
    void Should_LogAndContinueFlux_When_DeadlineExceededWrappedInPiiDetectionException_ForAttachment() {
        // Given: A space with a page containing an attachment
        String spaceKey = "AHVIV";
        String pageId = "43679749";
        String attachmentName = "PLZO_CSV_WGS84.csv";
        
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "Test Space", "http://test.com", "description",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
                .id(pageId)
                .title("Test Page")
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("Some content"))
                .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));

        AttachmentInfo attachment = new AttachmentInfo(attachmentName, "csv", "text/csv", "http://file");
        when(confluenceAttachmentService.getPageAttachments(pageId)).thenReturn(CompletableFuture.completedFuture(List.of(attachment)));
        when(confluenceDownloadService.downloadAttachmentContent(pageId, attachmentName))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("CSV content".getBytes(StandardCharsets.UTF_8))));
        when(attachmentTextExtractionService.extractText(any(), any())).thenReturn(Optional.of("Extracted CSV text"));

        // When: PiiDetectorClient throws PiiDetectionException wrapping StatusRuntimeException with DEADLINE_EXCEEDED
        StatusRuntimeException grpcException = new StatusRuntimeException(
                Status.DEADLINE_EXCEEDED.withDescription("deadline exceeded after 299997000000ns")
        );
        PiiDetectionException.PiiDetectionServiceException wrappedException = 
                PiiDetectionException.serviceError("Failed to analyze content for PII for pageId=null: DEADLINE_EXCEEDED: deadline exceeded after 299997000000ns", grpcException);
        
        when(piiDetectorClient.analyzeContent(any())).thenThrow(wrappedException);

        // Then: The flux should continue and emit an error event (not break the stream)
        Flux<ScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .filter(ev -> List.of(
                        ScanEventType.START.toJson(),
                        ScanEventType.ERROR.toJson(),
                        ScanEventType.PAGE_START.toJson(),
                        ScanEventType.ITEM.toJson(),
                        ScanEventType.PAGE_COMPLETE.toJson(),
                        ScanEventType.COMPLETE.toJson()
                ).contains(ev.eventType()))
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
                // Should emit ERROR event for attachment (not break flux)
                .expectNextMatches(ev -> {
                    boolean isError = ScanEventType.ERROR.toJson().equals(ev.eventType());
                    boolean hasCorrectPageId = pageId.equals(ev.pageId());
                    boolean mentionsTimeout = ev.message() != null && 
                            (ev.message().contains("DEADLINE_EXCEEDED") || ev.message().contains("timeout"));
                    
                    // CURRENTLY FAILS: The code doesn't detect DEADLINE_EXCEEDED in wrapped exceptions
                    // Expected: ERROR event with timeout/deadline message
                    // Actual: [ERROR][GENERAL] without detecting gRPC DEADLINE_EXCEEDED
                    return isError && hasCorrectPageId && mentionsTimeout;
                })
                // Should continue processing page content
                .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.ITEM.toJson().equals(ev.eventType()) || ScanEventType.ERROR.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should_LogAndContinueFlux_When_DeadlineExceededWrappedInPiiDetectionException_ForPageContent")
    void Should_LogAndContinueFlux_When_DeadlineExceededWrappedInPiiDetectionException_ForPageContent() {
        // Given: A space with a page (no attachments)
        String spaceKey = "AHVIV";
        String pageId = "43679750";
        
        ConfluenceSpace space = new ConfluenceSpace("id", spaceKey, "Test Space", "http://test.com", "description",
                ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceService.getSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(Optional.of(space)));

        ConfluencePage page = ConfluencePage.builder()
                .id(pageId)
                .title("Test Page with Timeout")
                .spaceKey(spaceKey)
                .content(new ConfluencePage.HtmlContent("Some content that causes timeout"))
                .build();
        when(confluenceService.getAllPagesInSpace(spaceKey)).thenReturn(CompletableFuture.completedFuture(List.of(page)));
        when(confluenceAttachmentService.getPageAttachments(pageId)).thenReturn(CompletableFuture.completedFuture(List.of()));

        // When: PiiDetectorClient throws PiiDetectionException wrapping StatusRuntimeException with DEADLINE_EXCEEDED
        StatusRuntimeException grpcException = new StatusRuntimeException(
                Status.DEADLINE_EXCEEDED.withDescription("deadline exceeded after 299998000000ns")
        );
        PiiDetectionException.PiiDetectionServiceException wrappedException = 
                PiiDetectionException.serviceError(
                        "Failed to analyze content for PII for pageId=" + pageId + ": DEADLINE_EXCEEDED: deadline exceeded after 299998000000ns", 
                        grpcException
                );
        
        when(piiDetectorClient.analyzeContent(any())).thenThrow(wrappedException);

        // Then: The flux should continue and emit an error event (not break the stream)
        Flux<ScanResult> flux = streamConfluenceScanUseCase.streamSpace(spaceKey)
                .filter(ev -> List.of(
                        ScanEventType.START.toJson(),
                        ScanEventType.PAGE_START.toJson(),
                        ScanEventType.ERROR.toJson(),
                        ScanEventType.PAGE_COMPLETE.toJson(),
                        ScanEventType.COMPLETE.toJson()
                ).contains(ev.eventType()))
                .timeout(Duration.ofSeconds(5));

        StepVerifier.create(flux)
                .expectNextMatches(ev -> ScanEventType.START.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.PAGE_START.toJson().equals(ev.eventType()))
                // Should emit ERROR event for page (not break flux)
                .expectNextMatches(ev -> {
                    boolean isError = ScanEventType.ERROR.toJson().equals(ev.eventType());
                    boolean hasCorrectPageId = pageId.equals(ev.pageId());
                    boolean mentionsTimeout = ev.message() != null && 
                            (ev.message().contains("DEADLINE_EXCEEDED") || ev.message().contains("timeout"));
                    
                    // CURRENTLY FAILS: The code doesn't detect DEADLINE_EXCEEDED in wrapped exceptions
                    // Expected: ERROR event with timeout/deadline message
                    // Actual: [ERROR][GENERAL] without detecting gRPC DEADLINE_EXCEEDED
                    return isError && hasCorrectPageId && mentionsTimeout;
                })
                .expectNextMatches(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()))
                .expectNextMatches(ev -> ScanEventType.COMPLETE.toJson().equals(ev.eventType()))
                .verifyComplete();
    }
}
