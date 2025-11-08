package pro.softcom.sentinelle.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.sentinelle.application.config.port.in.GetPollingConfigPort;
import pro.softcom.sentinelle.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.sentinelle.application.config.usecase.GetPollingConfigUseCase;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceSpaceCacheRefreshService;
import pro.softcom.sentinelle.application.confluence.usecase.ConfluenceUseCaseImpl;
import pro.softcom.sentinelle.application.pii.export.DetectionReportMapper;
import pro.softcom.sentinelle.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.sentinelle.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.sentinelle.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.RevealPiiSecretsPort;
import pro.softcom.sentinelle.application.pii.reporting.port.in.ScanReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.AfterCommitExecutionPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ReadPiiConfigPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
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
import pro.softcom.sentinelle.application.pii.reporting.usecase.PauseScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.RevealPiiSecretsUseCase;
import pro.softcom.sentinelle.application.pii.reporting.usecase.ScanReportingUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceResumeScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceScanUseCaseImpl;
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
    public ConfluenceUseCase confluenceUseCase(ConfluenceClient confluenceClient,
                                                ConfluenceSpaceRepository spaceRepository) {
        return new ConfluenceUseCaseImpl(confluenceClient, spaceRepository);
    }

    @Bean
    public ScanReportingUseCase scanResultUseCase(ScanResultQuery scanResultQuery,
                                                  ScanCheckpointRepository checkpointRepo) {
        return new ScanReportingUseCaseImpl(scanResultQuery, checkpointRepo);
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
    public ScanOrchestrator scanOrchestrator(ScanEventFactory scanEventFactory,
                                             ScanProgressCalculator scanProgressCalculator,
                                             ScanCheckpointService scanCheckpointService,
                                             ScanEventStore scanEventStore,
                                             ScanEventDispatcher scanEventDispatcher) {
        return new ScanOrchestrator(
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
    public StreamConfluenceScanUseCase streamConfluenceScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ScanOrchestrator scanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanTimeoutConfig scanTimeoutConfig) {
        return new StreamConfluenceScanUseCaseImpl(
                confluenceAccessor,
                piiDetectorClient,
                scanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig
        );
    }

    @Bean
    public StreamConfluenceResumeScanUseCase streamConfluenceResumeScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ScanOrchestrator scanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanCheckpointRepository scanCheckpointRepository,
            ScanTimeoutConfig scanTimeoutConfig) {
        return new StreamConfluenceResumeScanUseCaseImpl(
                confluenceAccessor,
                piiDetectorClient,
                scanOrchestrator,
                attachmentProcessor,
                scanCheckpointRepository,
                scanTimeoutConfig
        );
    }

    @Bean
    public PauseScanUseCase pauseScanUseCase(ScanCheckpointRepository scanCheckpointRepository) {
        return new PauseScanUseCaseImpl(scanCheckpointRepository);
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
