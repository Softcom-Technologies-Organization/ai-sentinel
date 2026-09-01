package pro.softcom.aisentinel.application.pii.reporting.usecase;

import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.aisentinel.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.aisentinel.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanSpaceStatsCollector;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanTimeFalsePositiveSuppressor;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;

/**
 * Cohesive bundle of the collaborators shared by every Confluence scan use case.
 *
 * <p>Groups the pipeline dependencies that {@link AbstractStreamConfluenceScanUseCase}
 * needs so concrete use cases stay below the parameter-count limit while only declaring
 * their own extra collaborators.
 *
 * @param confluenceAccessor        access to Confluence spaces, pages and attachments
 * @param piiDetectorClient         client invoking the PII detector
 * @param contentScanOrchestrator   orchestrates event creation, progress and persistence
 * @param attachmentProcessor       extracts text from page attachments
 * @param scanTimeoutConfig         per-call PII detection timeouts
 * @param htmlContentParser         cleans raw HTML page content before detection
 * @param scanSpaceStatsCollector   accumulates per-space scan statistics
 * @param pageConcurrency           number of pages whose PII detection runs
 *                                  concurrently (1 = sequential, the historical
 *                                  behaviour); feeds the detector worker pool
 * @param falsePositiveSuppressor   drops detections already flagged false positive before they
 *                                  reach any store or the live view; {@code null} disables
 *                                  scan-time suppression (used by tests that do not exercise it)
 */
public record ScanPipelineDependencies(
    ConfluenceAccessor confluenceAccessor,
    PiiDetectorClient piiDetectorClient,
    ContentScanOrchestrator contentScanOrchestrator,
    AttachmentProcessor attachmentProcessor,
    ScanTimeOutConfig scanTimeoutConfig,
    HtmlContentParser htmlContentParser,
    ScanSpaceStatsCollector scanSpaceStatsCollector,
    int pageConcurrency,
    ScanTimeFalsePositiveSuppressor falsePositiveSuppressor
) {

    /**
     * Backward-compatible constructor keeping the configured page concurrency but without
     * scan-time false-positive suppression. Kept so existing call sites (tests) compile
     * unchanged.
     */
    public ScanPipelineDependencies(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ContentScanOrchestrator contentScanOrchestrator,
        AttachmentProcessor attachmentProcessor,
        ScanTimeOutConfig scanTimeoutConfig,
        HtmlContentParser htmlContentParser,
        ScanSpaceStatsCollector scanSpaceStatsCollector,
        int pageConcurrency
    ) {
        this(confluenceAccessor, piiDetectorClient, contentScanOrchestrator, attachmentProcessor,
             scanTimeoutConfig, htmlContentParser, scanSpaceStatsCollector, pageConcurrency, null);
    }

    /**
     * Backward-compatible constructor defaulting page concurrency to 1
     * (sequential page processing — the historical behaviour) and disabling scan-time
     * false-positive suppression. Kept so existing call sites (tests) compile unchanged;
     * production wiring uses the canonical constructor with the configured value and the
     * suppressor.
     */
    public ScanPipelineDependencies(
        ConfluenceAccessor confluenceAccessor,
        PiiDetectorClient piiDetectorClient,
        ContentScanOrchestrator contentScanOrchestrator,
        AttachmentProcessor attachmentProcessor,
        ScanTimeOutConfig scanTimeoutConfig,
        HtmlContentParser htmlContentParser,
        ScanSpaceStatsCollector scanSpaceStatsCollector
    ) {
        this(confluenceAccessor, piiDetectorClient, contentScanOrchestrator, attachmentProcessor,
             scanTimeoutConfig, htmlContentParser, scanSpaceStatsCollector, 1, null);
    }
}
