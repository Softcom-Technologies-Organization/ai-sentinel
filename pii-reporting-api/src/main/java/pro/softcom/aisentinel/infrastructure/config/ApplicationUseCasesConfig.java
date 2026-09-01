package pro.softcom.aisentinel.infrastructure.config;

import pro.softcom.aisentinel.application.confluence.port.out.*;
import pro.softcom.aisentinel.application.pii.reporting.port.in.*;
import pro.softcom.aisentinel.application.pii.reporting.port.out.*;
import pro.softcom.aisentinel.application.pii.reporting.service.*;
import pro.softcom.aisentinel.application.pii.reporting.usecase.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.application.config.port.in.GetPollingConfigPort;
import pro.softcom.aisentinel.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.aisentinel.application.config.usecase.GetPollingConfigUseCase;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpacePort;
import pro.softcom.aisentinel.application.confluence.port.in.ConfluenceSpaceUpdateInfoPort;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceSpaceCacheRefreshService;
import pro.softcom.aisentinel.application.confluence.usecase.FetchConfluenceSpaceContentUseCase;
import pro.softcom.aisentinel.application.confluence.usecase.FetchSpaceUpdateInfoUseCase;
import pro.softcom.aisentinel.application.confluence.usecase.ManageConfluenceConnectionUseCase;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageConcurrencyBenchmarkPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManageConcurrencyBenchmarkUseCase;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManagePiiDetectionConfigUseCase;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManagePiiTypeConfigsUseCase;
import pro.softcom.aisentinel.application.pii.export.DetectionReportMapper;
import pro.softcom.aisentinel.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.aisentinel.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.aisentinel.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanTimeFalsePositiveSuppressor;
import pro.softcom.aisentinel.application.pii.reporting.ScanPiiTypeCountService;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.application.pii.security.PiiAccessAuditService;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceCloudHttpClientAdapter;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.ConfluenceDataCenterHttpClientAdapter;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.DelegatingConfluenceClient;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfigUpdatedEvent;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

/**
 * Spring configuration that wires application use cases as beans from the infrastructure layer.
 * Business intent: keep the application layer free of framework annotations while exposing ports
 * to inbound adapters (controllers) via Spring beans.
 */
@Configuration
public class ApplicationUseCasesConfig {

    @Bean
    public ConfluenceClient delegatingConfluenceClient(
            @Qualifier("confluenceConfig") ConfluenceConnectionConfig confluenceConfig,
            ObjectMapper objectMapper) {
        var cloudAdapter = new ConfluenceCloudHttpClientAdapter(confluenceConfig, objectMapper);
        var dataCenterAdapter = new ConfluenceDataCenterHttpClientAdapter(confluenceConfig, objectMapper);
        return new DelegatingConfluenceClient(confluenceConfig, cloudAdapter, dataCenterAdapter);
    }

    @Bean
    public ConfluenceSpacePort confluenceUseCase(ConfluenceClient confluenceClient,
                                                 ConfluenceSpaceRepository spaceRepository) {
        return new FetchConfluenceSpaceContentUseCase(confluenceClient, spaceRepository);
    }

    @Bean
    public FalsePositiveDetectionFilter falsePositiveDetectionFilter(
            FindingRemediationStore findingRemediationStore,
            ScanEventFindingResolver scanEventFindingResolver,
            SeverityCalculationService severityCalculationService,
            ScanResultQuery scanResultQuery) {
        return new FalsePositiveDetectionFilter(findingRemediationStore, scanEventFindingResolver,
                severityCalculationService, scanResultQuery);
    }

    @Bean
    public ScanReportingPort scanResultUseCase(ScanResultQuery scanResultQuery,
                                               ScanCheckpointRepository checkpointRepo,
                                               ConfluenceSpaceRepository spaceRepository,
                                               ScanSeverityCountService scanSeverityCountService,
                                               ScanPiiTypeCountService scanPiiTypeCountService,
                                               FalsePositiveDetectionFilter falsePositiveDetectionFilter) {
        return new ScanReportingUseCase(scanResultQuery, checkpointRepo, spaceRepository,
                scanSeverityCountService, scanPiiTypeCountService, falsePositiveDetectionFilter);
    }

    @Bean
    public ScanProgressCalculator scanProgressCalculator() {
        return new ScanProgressCalculator();
    }

    @Bean
    public ScanEventFactory scanEventFactory(ConfluenceUrlProvider confluenceUrlProvider,
                                             PiiContextExtractor piiContextExtractor,
                                             SeverityCalculationService severityCalculationService,
                                             ValueFingerprintCalculator valueFingerprintCalculator) {
        return new ScanEventFactory(confluenceUrlProvider, piiContextExtractor, severityCalculationService,
                                    valueFingerprintCalculator);
    }

    @Bean
    public ScanCheckpointService scanCheckpointService(ScanCheckpointRepository scanCheckpointRepository) {
        return new ScanCheckpointService(scanCheckpointRepository);
    }

    @Bean
    public ConfluenceAccessor confluenceAccessor(ConfluenceClient confluenceClient,
                                                  ConfluenceAttachmentClient confluenceAttachmentClient,
                                                  ConfluenceSpaceRepository spaceRepository) {
        return new ConfluenceAccessor(confluenceClient, confluenceAttachmentClient, spaceRepository);
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
                                                    ScanEventDispatcher scanEventDispatcher,
                                                    SeverityCalculationService severityCalculationService,
                                                    ScanSeverityCountService scanSeverityCountService,
                                                    ScanPiiTypeCountService scanPiiTypeCountService) {
        return new ContentScanOrchestrator(
                scanEventFactory,
                scanProgressCalculator,
                scanCheckpointService,
                scanEventStore,
                scanEventDispatcher,
                severityCalculationService,
                scanSeverityCountService,
                scanPiiTypeCountService
        );
    }

    @Bean
    public ScanTimeFalsePositiveSuppressor scanTimeFalsePositiveSuppressor(
            FindingRemediationStore findingRemediationStore,
            ScanEventFindingResolver scanEventFindingResolver,
            SeverityCalculationService severityCalculationService) {
        return new ScanTimeFalsePositiveSuppressor(findingRemediationStore, scanEventFindingResolver,
                severityCalculationService);
    }

    @Bean
    public AttachmentProcessor attachmentProcessor(
            ConfluenceAttachmentDownloader confluenceDownloadService,
            AttachmentTextExtractor attachmentTextExtractionService) {
        return new AttachmentProcessor(confluenceDownloadService, attachmentTextExtractionService);
    }

    @Bean
    public ScanSpaceStatsCollector scanSpaceStatsCollector(ScanSpaceStatsRepository scanSpaceStatsRepository) {
        return new ScanSpaceStatsCollector(scanSpaceStatsRepository);
    }

    @Bean
    public GetScanSpaceStatsPort getScanSpaceStatsPort(
            ScanCheckpointRepository scanCheckpointRepository,
            ScanSpaceStatsRepository scanSpaceStatsRepository,
            FailedScanItemQuery failedScanItemQuery) {
        return new GetScanSpaceStatsUseCase(scanCheckpointRepository, scanSpaceStatsRepository, failedScanItemQuery);
    }

    @Bean
    public ScanPipelineDependencies scanPipelineDependencies(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanTimeOutConfig scanTimeoutConfig,
            HtmlContentParser htmlContentParser,
            ScanSpaceStatsCollector scanSpaceStatsCollector,
            ScanTimeFalsePositiveSuppressor scanTimeFalsePositiveSuppressor,
            @Value("${scan.page-concurrency:1}") int pageConcurrency) {
        return new ScanPipelineDependencies(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig,
                htmlContentParser,
                scanSpaceStatsCollector,
                pageConcurrency,
                scanTimeFalsePositiveSuppressor
        );
    }

    @Bean
    public StreamConfluenceScanPort streamConfluenceScanUseCase(
            ScanPipelineDependencies scanPipelineDependencies,
            PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort) {
        return new StreamConfluenceScanUseCase(
                scanPipelineDependencies,
                personallyIdentifiableInformationScanExecutionOrchestratorPort
        );
    }

    @Bean
    public StreamConfluenceResumeScanPort streamConfluenceResumeScanUseCase(
            ScanPipelineDependencies scanPipelineDependencies,
            ScanCheckpointRepository scanCheckpointRepository,
            PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort) {
        return new StreamConfluenceResumeScanUseCase(
                scanPipelineDependencies,
                scanCheckpointRepository,
                personallyIdentifiableInformationScanExecutionOrchestratorPort
        );
    }

    @Bean
    public PauseScanPort pauseScanUseCase(ScanCheckpointRepository scanCheckpointRepository,
                                          PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort) {
        return new PauseScanUseCase(scanCheckpointRepository,
                                    personallyIdentifiableInformationScanExecutionOrchestratorPort);
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
            ReadExportContextPort readExportContextPort,
            FalsePositiveDetectionFilter falsePositiveDetectionFilter) {
        return new ExportDetectionReportUseCase(
                readScanEventsPort,
                writeDetectionReportPort,
                detectionReportMapper,
                readExportContextPort,
                falsePositiveDetectionFilter
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

    @Bean
    public ManagePiiDetectionConfigPort managePiiDetectionConfigPort(PiiDetectionConfigRepository piiDetectionConfigRepository) {
        return new ManagePiiDetectionConfigUseCase(piiDetectionConfigRepository);
    }

    @Bean
    public ManageConcurrencyBenchmarkPort manageConcurrencyBenchmarkPort(PiiDetectionConfigRepository piiDetectionConfigRepository) {
        return new ManageConcurrencyBenchmarkUseCase(piiDetectionConfigRepository);
    }

    @Bean
    public ManagePiiTypeConfigsPort managePiiTypeConfigsPort(
        PiiTypeConfigRepository piiTypeConfigRepository) {
        return new ManagePiiTypeConfigsUseCase(piiTypeConfigRepository);
    }

    @Bean
    public ManageConfluenceConnectionPort manageConfluenceConnectionPort(
            ConfluenceConnectionConfigRepository confluenceConnectionConfigRepository,
            EncryptionService encryptionService,
            ApplicationEventPublisher eventPublisher) {
        return new ManageConfluenceConnectionUseCase(
                confluenceConnectionConfigRepository,
                encryptionService,
                () -> eventPublisher.publishEvent(new ConfluenceConfigUpdatedEvent(this))
        );
    }
}