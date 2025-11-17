package pro.softcom.sentinelle.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.sentinelle.application.config.port.in.GetPollingConfigPort;
import pro.softcom.sentinelle.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.sentinelle.application.config.usecase.GetPollingConfigUseCase;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceSpacePort;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceSpaceUpdateInfoPort;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceSpaceCacheRefreshService;
import pro.softcom.sentinelle.application.confluence.usecase.FetchConfluenceSpaceContentUseCase;
import pro.softcom.sentinelle.application.confluence.usecase.FetchSpaceUpdateInfoUseCase;
import pro.softcom.sentinelle.application.pii.export.DetectionReportMapper;
import pro.softcom.sentinelle.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.sentinelle.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.RevealPiiSecretsPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.ScanReportingPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.AfterCommitExecutionPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ReadPiiConfigPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanTimeOutConfig;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.ContentScanOrchestrator;
import pro.softcom.sentinelle.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventDispatcher;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.sentinelle.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.sentinelle.application.pii.reporting.usecase.PauseScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.usecase.RevealPiiSecretsUseCase;
import pro.softcom.sentinelle.application.pii.reporting.usecase.ScanReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.application.pii.security.PiiAccessAuditService;
import pro.softcom.sentinelle.application.pii.security.ScanResultEncryptor;
import pro.softcom.sentinelle.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;

/**
 * Spring configuration that wires application use cases as beans from the infrastructure layer.
 * Business intent: keep the application layer free of framework annotations while exposing ports
 * to inbound adapters (controllers) via Spring beans.
 */
@Configuration
public class ApplicationUseCasesConfig {

    @Bean
    public ConfluenceSpacePort confluenceUseCase(ConfluenceClient confluenceClient,
                                                 ConfluenceSpaceRepository spaceRepository) {
        return new FetchConfluenceSpaceContentUseCase(confluenceClient, spaceRepository);
    }

    @Bean
    public ScanReportingPort scanResultUseCase(ScanResultQuery scanResultQuery,
                                               ScanCheckpointRepository checkpointRepo) {
        return new ScanReportingUseCase(scanResultQuery, checkpointRepo);
    }

    @Bean
    public ScanProgressCalculator scanProgressCalculator() {
        return new ScanProgressCalculator();
    }

    @Bean
    public ScanEventFactory scanEventFactory(ConfluenceUrlProvider confluenceUrlProvider,
                                             PiiContextExtractor piiContextExtractor) {
        return new ScanEventFactory(confluenceUrlProvider, piiContextExtractor);
    }

    @Bean
    public ScanCheckpointService scanCheckpointService(ScanCheckpointRepository scanCheckpointRepository) {
        return new ScanCheckpointService(scanCheckpointRepository);
    }

    @Bean
    public ConfluenceAccessor confluenceAccessor(ConfluenceClient confluenceClient,
                                                  ConfluenceAttachmentClient confluenceAttachmentClient) {
        return new ConfluenceAccessor(confluenceClient, confluenceAttachmentClient);
    }

    @Bean
    public ScanEventDispatcher scanEventDispatcher(PublishEventPort publishEventPort,
                                                   AfterCommitExecutionPort afterCommitExecutionPort) {
        return new ScanEventDispatcher(publishEventPort, afterCommitExecutionPort);
    }

    @Bean
    public ContentScanOrchestrator scanOrchestrator(ScanEventFactory scanEventFactory,
                                                    ScanProgressCalculator scanProgressCalculator,
                                                    ScanCheckpointService scanCheckpointService,
                                                    ScanEventStore scanEventStore,
                                                    ScanEventDispatcher scanEventDispatcher) {
        return new ContentScanOrchestrator(
                scanEventFactory, scanProgressCalculator, scanCheckpointService, scanEventStore, scanEventDispatcher
        );
    }

    @Bean
    public AttachmentProcessor attachmentProcessor(
            ConfluenceAttachmentDownloader confluenceDownloadService,
            AttachmentTextExtractor attachmentTextExtractionService) {
        return new AttachmentProcessor(confluenceDownloadService, attachmentTextExtractionService);
    }

    @Bean
    public StreamConfluenceScanPort streamConfluenceScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanTimeOutConfig scanTimeoutConfig) {
        return new StreamConfluenceScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig
        );
    }

    @Bean
    public StreamConfluenceResumeScanPort streamConfluenceResumeScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanCheckpointRepository scanCheckpointRepository,
            ScanTimeOutConfig scanTimeoutConfig) {
        return new StreamConfluenceResumeScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanCheckpointRepository,
                scanTimeoutConfig
        );
    }

    @Bean
    public PauseScanPort pauseScanUseCase(ScanCheckpointRepository scanCheckpointRepository) {
        return new PauseScanUseCase(scanCheckpointRepository);
    }

    @Bean
    public ConfluenceSpaceUpdateInfoPort getSpaceUpdateInfoUseCase(
        ConfluenceSpacePort confluenceSpacePort,
        ConfluenceClient confluenceClient,
        ScanCheckpointRepository scanCheckpointRepository
    ){
        return new FetchSpaceUpdateInfoUseCase(confluenceSpacePort, confluenceClient, scanCheckpointRepository);
    }

    @Bean
    public ConfluenceSpaceCacheRefreshService confluenceSpaceCacheRefreshService(
            ConfluenceClient confluenceClient,
            ConfluenceSpaceRepository spaceRepository
    ) {
        return new ConfluenceSpaceCacheRefreshService(confluenceClient, spaceRepository);
    }

    // Content Parsers
    @Bean
    public PlainTextParser plainTextParser() {
        return new PlainTextParser();
    }

    @Bean
    public HtmlContentParser htmlContentParser() {
        return new HtmlContentParser();
    }

    @Bean
    public ContentParserFactory contentParserFactory(PlainTextParser plainTextParser,
                                                     HtmlContentParser htmlContentParser) {
        return new ContentParserFactory(plainTextParser, htmlContentParser);
    }

    // PII Services
    @Bean
    public PiiContextExtractor piiContextExtractor(ContentParserFactory contentParserFactory) {
        return new PiiContextExtractor(contentParserFactory);
    }

    @Bean
    public ScanResultEncryptor scanResultEncryptor(EncryptionService encryptionService) {
        return new ScanResultEncryptor(encryptionService);
    }

    // Export Services
    @Bean
    public DetectionReportMapper detectionReportMapper() {
        return new DetectionReportMapper();
    }

    @Bean
    public ExportDetectionReportPort exportDetectionReportPort(
            ReadScanEventsPort readScanEventsPort,
            WriteDetectionReportPort writeDetectionReportPort,
            DetectionReportMapper detectionReportMapper,
            ReadExportContextPort readExportContextPort) {
        return new ExportDetectionReportUseCase(
                readScanEventsPort,
                writeDetectionReportPort,
                detectionReportMapper,
                readExportContextPort
        );
    }

    // Security Services
    @Bean
    public PiiAccessAuditService piiAccessAuditService(
            SavePiiAuditPort savePiiAuditPort,
            @Value("${pii.audit.retention-days:730}") int retentionDays) {
        return new PiiAccessAuditService(savePiiAuditPort, retentionDays);
    }

    // Configuration Services
    @Bean
    public GetPollingConfigPort getPollingConfigPort(ReadConfluenceConfigPort readConfluenceConfigPort) {
        return new GetPollingConfigUseCase(readConfluenceConfigPort);
    }

    // PII Access Services
    @Bean
    public RevealPiiSecretsPort revealPiiSecretsPort(
            ReadPiiConfigPort readPiiConfigPort,
            ScanResultQuery scanResultQuery) {
        return new RevealPiiSecretsUseCase(readPiiConfigPort, scanResultQuery);
    }
}
