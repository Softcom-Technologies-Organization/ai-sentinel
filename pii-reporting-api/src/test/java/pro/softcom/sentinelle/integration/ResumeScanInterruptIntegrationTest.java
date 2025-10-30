package pro.softcom.sentinelle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.sentinelle.SentinelleApplication;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.DataOwners;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.in.dto.ScanEventType;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;

@Testcontainers
@SpringBootTest(classes = SentinelleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ResumeScanInterruptIntegrationTest.TestConfluenceBeans.class,
    TestPiiDetectionClientConfiguration.class})
@TestInstance(Lifecycle.PER_CLASS)
class ResumeScanInterruptIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
                     () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private StreamConfluenceScanUseCase streamConfluenceScanUseCase;

    @Autowired
    private StreamConfluenceResumeScanUseCase streamConfluenceResumeScanUseCase;

    @Autowired
    private DetectionEventRepository eventRepo;

    @Autowired
    private DetectionCheckpointRepository checkpointRepo;

    @Autowired
    private ConfluenceClient confluenceClient;

    @Autowired
    private ConfluenceAttachmentClient confluenceAttachmentClient;

    @Autowired
    private ConfluenceAttachmentDownloader confluenceAttachmentDownloader;

    @Autowired
    private ConfluenceUrlProvider confluenceUrlProvider;

    @Autowired
    private AttachmentTextExtractor attachmentTextExtractor;

    @Test
    void Should_ResumeFromNextPage_When_ScanInterrupted() {
        // Arrange: program Mockito stubs for deterministic environment
        var space = new ConfluenceSpace("id-TEST", "TEST", "Test Space",
                                        "http://test.com", "Test description",
                                        ConfluenceSpace.SpaceType.GLOBAL,
                                        ConfluenceSpace.SpaceStatus.CURRENT,
                                        new DataOwners.NotLoaded());
        var p1 = ConfluencePage.builder().id("p1").title("Page 1").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 1")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(),
                                                1, "current")).labels(List.of())
            .customProperties(null).build();
        var p2 = ConfluencePage.builder().id("p2").title("Page 2").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 2")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(),
                                                1, "current")).labels(List.of())
            .customProperties(null).build();
        var p3 = ConfluencePage.builder().id("p3").title("Page 3").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 3")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(),
                                                1, "current")).labels(List.of())
            .customProperties(null).build();

        when(confluenceClient.getAllSpaces()).thenReturn(
            CompletableFuture.completedFuture(List.of(space)));
        when(confluenceClient.getAllPagesInSpace("TEST")).thenReturn(
            CompletableFuture.completedFuture(List.of(p1, p2, p3)));
        when(confluenceAttachmentClient.getPageAttachments(anyString())).thenReturn(
            CompletableFuture.completedFuture(List.of()));
        when(confluenceUrlProvider.baseUrl()).thenReturn("http://example");

        // Act 1: Start a multi-space scan, and interrupt right after first page_complete is recorded
        AtomicReference<String> scanIdRef = new AtomicReference<>();

        // Phase 1: subscribe and stop the upstream pipeline exactly at first page_complete
        var firstPhase = streamConfluenceScanUseCase.streamAllSpaces().doOnNext(ev -> {
                scanIdRef.compareAndSet(null, ev.scanId());
            }).takeUntil(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType())).collectList()
            .block(Duration.ofSeconds(15));

        assertThat(firstPhase).as("first phase should emit at least one event").isNotNull()
            .isNotEmpty();
        String scanId = scanIdRef.get();
        assertThat(scanId).isNotBlank();

        var afterInterrupt = eventRepo.findByScanIdAndEventTypeInOrderByEventSeqAsc(scanId, List.of(
            ScanEventType.PAGE_COMPLETE.toJson()));
        assertThat(afterInterrupt).hasSize(1);
        String firstDonePage = afterInterrupt.getFirst().getPageId();

        // Act 2: Resume and let it complete
        List<String> resumedEvents = new ArrayList<>();
        streamConfluenceResumeScanUseCase.resumeAllSpaces(scanId)
            .doOnNext(ev -> resumedEvents.add(ev.eventType() + ":" + ev.pageId())).blockLast();

        // Determine expected next page after the interrupted one
        List<String> orderedPages = List.of("p1", "p2", "p3");
        int idx = orderedPages.indexOf(firstDonePage);
        String expectedNext = (idx >= 0 && idx + 1 < orderedPages.size()) ? orderedPages.get(idx + 1) : null;
        var resumedPageStarts = resumedEvents.stream().filter(s -> s.startsWith("pageStart:")).toList();

        // Assert at resume-time: continuity and no duplicate of already completed page
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(resumedEvents).as("resumed stream should emit events").isNotEmpty();
        softly.assertThat(resumedEvents)
            .as("resume must not re-emit already completed page")
            .doesNotContain("pageStart:" + firstDonePage, "pageComplete:" + firstDonePage);
        if (expectedNext != null) {
            softly.assertThat(resumedPageStarts)
                .as("resume should start from next page").contains("pageStart:" + expectedNext);
        }

        // Assert (global): page_complete events are exactly 3 unique page_ids, no duplicates of the first
        var allPageCompletes = eventRepo.findByScanIdAndEventTypeInOrderByEventSeqAsc(scanId,
                                                                                      List.of(
                                                                                          ScanEventType.PAGE_COMPLETE.toJson()));
        var pageIds = allPageCompletes.stream().map(e -> e.getPageId()).toList();

        softly.assertThat(allPageCompletes).hasSize(3);
        softly.assertThat(pageIds).doesNotContainNull();
        softly.assertThat(pageIds).doesNotHaveDuplicates();
        softly.assertThat(pageIds.getFirst()).isEqualTo(firstDonePage);

        // Persisted checkpoint must indicate completion on last page
        var cpOpt = checkpointRepo.findByScanIdAndSpaceKey(scanId, "TEST");
        softly.assertThat(cpOpt).isPresent();
        cpOpt.ifPresent(cp -> {
            softly.assertThat(cp.getStatus()).isEqualTo("COMPLETED");
            softly.assertThat(cp.getLastProcessedPageId()).isEqualTo("p3");
        });

        softly.assertAll();
    }

    @Test
    void Should_ReportCorrectProgressPercentages_When_ResumingScan() {
        // Arrange: deterministic environment (1 space, 3 pages, no attachments)
        var space = new ConfluenceSpace("id-TEST", "TEST", "Test Space", 
                                        "http://test.com", "Test description",
                                        ConfluenceSpace.SpaceType.GLOBAL, 
                                        ConfluenceSpace.SpaceStatus.CURRENT,
                                        new DataOwners.NotLoaded());
        var p1 = ConfluencePage.builder().id("p1").title("Page 1").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 1")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(), 1, "current"))
            .labels(List.of()).customProperties(null).build();
        var p2 = ConfluencePage.builder().id("p2").title("Page 2").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 2")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(), 1, "current"))
            .labels(List.of()).customProperties(null).build();
        var p3 = ConfluencePage.builder().id("p3").title("Page 3").spaceKey("TEST")
            .content(new ConfluencePage.HtmlContent("hello 3")).metadata(
                new ConfluencePage.PageMetadata("u", LocalDateTime.now(), "u", LocalDateTime.now(), 1, "current"))
            .labels(List.of()).customProperties(null).build();

        when(confluenceClient.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(space)));
        when(confluenceClient.getAllPagesInSpace("TEST")).thenReturn(CompletableFuture.completedFuture(List.of(p1, p2, p3)));
        when(confluenceAttachmentClient.getPageAttachments(anyString())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(confluenceUrlProvider.baseUrl()).thenReturn("http://example");

        // Phase 1: start and interrupt at first page_complete
        AtomicReference<String> scanIdRef = new AtomicReference<>();
        var firstPhase = streamConfluenceScanUseCase.streamAllSpaces()
            .doOnNext(ev -> scanIdRef.compareAndSet(null, ev.scanId()))
            .takeUntil(ev -> ScanEventType.PAGE_COMPLETE.toJson().equals(ev.eventType()))
            .collectList()
            .block(Duration.ofSeconds(20));

        assertThat(firstPhase).isNotNull().isNotEmpty();
        String scanId = scanIdRef.get();
        assertThat(scanId).isNotBlank();

        // Sanity check: exactly 1 page_complete persisted so far
        var afterInterrupt = eventRepo.findByScanIdAndEventTypeInOrderByEventSeqAsc(scanId, List.of(ScanEventType.PAGE_COMPLETE.toJson()));
        assertThat(afterInterrupt).hasSize(1);

        // Phase 2: resume and collect resumed events
        var resumed = streamConfluenceResumeScanUseCase.resumeAllSpaces(scanId).collectList().block(Duration.ofSeconds(20));
        assertThat(resumed).isNotNull().isNotEmpty();

        // Filter only our TEST space (single space anyway)
        var resumedStart = resumed.stream().filter(e -> ScanEventType.START.toJson().equals(e.eventType())).findFirst();
        var resumedPageStarts = resumed.stream().filter(e -> ScanEventType.PAGE_START.toJson().equals(e.eventType())).toList();
        var resumedItems = resumed.stream().filter(e -> ScanEventType.ITEM.toJson().equals(e.eventType())).toList();
        var resumedPageCompletes = resumed.stream().filter(e -> ScanEventType.PAGE_COMPLETE.toJson().equals(e.eventType())).toList();
        var resumedComplete = resumed.stream().filter(e -> ScanEventType.COMPLETE.toJson().equals(e.eventType())).findFirst();

        SoftAssertions softly = new SoftAssertions();

        // Expect start with remaining total = 2 and cumulative progress around 33%
        softly.assertThat(resumedStart).isPresent();
        resumedStart.ifPresent(e -> {
            softly.assertThat(e.pagesTotal()).as("pagesTotal after resume").isEqualTo(2);
            softly.assertThat(e.analysisProgressPercentage()).as("progress after resume start").isNotNull()
                .isBetween(33.0, 34.0);
        });

        // Expect first resumed page (p2) to report ~33% at page_start then ~66% at item and ~66% at page_complete
        softly.assertThat(resumedPageStarts).isNotEmpty();
        softly.assertThat(resumedItems).isNotEmpty();
        softly.assertThat(resumedPageCompletes).hasSize(2);

        // Build a sequence of progress values and assert monotonic non-decreasing ending at 100%
        var progressSeq = resumed.stream()
            .map(ScanResult::analysisProgressPercentage)
            .filter(Objects::nonNull)
            .toList();
        softly.assertThat(progressSeq).isNotEmpty();
        softly.assertThat(progressSeq.getLast()).isEqualTo(100.0);
        for (int i = 1; i < progressSeq.size(); i++) {
            softly.assertThat(progressSeq.get(i)).as("progress non-decreasing at idx=" + i)
                .isGreaterThanOrEqualTo(progressSeq.get(i - 1));
        }

        // Detailed checks on page_complete percentages: should be ~66% then 100%
        softly.assertThat(resumedPageCompletes.get(0).analysisProgressPercentage()).isBetween(66.0, 67.0);
        softly.assertThat(resumedPageCompletes.get(1).analysisProgressPercentage()).isEqualTo(100.0);

        // Ensure already completed page (p1) is not present in resumed page events
        var resumedPageIds = resumed.stream()
            .filter(e -> ScanEventType.PAGE_START.toJson().equals(e.eventType()) || ScanEventType.PAGE_COMPLETE.toJson().equals(e.eventType()))
            .map(ScanResult::pageId)
            .toList();
        softly.assertThat(resumedPageIds).doesNotContain("p1");

        // Footer complete remains at 100%
        softly.assertThat(resumedComplete).isPresent();
        resumedComplete.ifPresent(e -> softly.assertThat(e.analysisProgressPercentage()).isEqualTo(100.0));

        softly.assertAll();
    }

    @TestConfiguration
    static class TestConfluenceBeans {

        @Bean
        @Primary
        ConfluenceClient confluenceClient() {
            return Mockito.mock(ConfluenceClient.class);
        }

        @Bean
        @Primary
        ConfluenceAttachmentClient confluenceAttachmentClient() {
            return Mockito.mock(ConfluenceAttachmentClient.class);
        }

        @Bean
        @Primary
        ConfluenceAttachmentDownloader confluenceAttachmentDownloader() {
            return Mockito.mock(ConfluenceAttachmentDownloader.class);
        }

        @Bean
        @Primary
        ConfluenceUrlProvider confluenceUrlProvider() {
            return Mockito.mock(ConfluenceUrlProvider.class);
        }

        @Bean
        @Primary
        AttachmentTextExtractor attachmentTextExtractor() {
            return Mockito.mock(
                AttachmentTextExtractor.class);
        }
    }
}
