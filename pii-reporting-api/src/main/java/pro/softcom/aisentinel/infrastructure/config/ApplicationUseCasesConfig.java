package pro.softcom.aisentinel.infrastructure.config;

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
import pro.softcom.aisentinel.application.confluence.port.out.*;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceAccessor;
import pro.softcom.aisentinel.application.confluence.service.ConfluenceSpaceCacheRefreshService;
import pro.softcom.aisentinel.application.confluence.usecase.FetchConfluenceSpaceContentUseCase;
import pro.softcom.aisentinel.application.confluence.usecase.FetchSpaceUpdateInfoUseCase;
import pro.softcom.aisentinel.application.confluence.usecase.ManageConfluenceConnectionUseCase;
import pro.softcom.aisentinel.application.jira.port.in.JiraProjectPort;
import pro.softcom.aisentinel.application.jira.port.in.ManageJiraConnectionPort;
import pro.softcom.aisentinel.application.jira.port.in.StreamJiraScanPort;
import pro.softcom.aisentinel.application.jira.port.out.JiraClient;
import pro.softcom.aisentinel.application.jira.port.out.JiraConnectionConfigRepository;
import pro.softcom.aisentinel.application.jira.port.out.JiraUrlProvider;
import pro.softcom.aisentinel.application.jira.service.AdfContentParser;
import pro.softcom.aisentinel.application.jira.service.JiraAccessor;
import pro.softcom.aisentinel.application.jira.usecase.FetchJiraProjectUseCase;
import pro.softcom.aisentinel.application.jira.usecase.ManageJiraConnectionUseCase;
import pro.softcom.aisentinel.application.jira.usecase.StreamJiraScanUseCase;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiTypeConfigsPort;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManagePiiDetectionConfigUseCase;
import pro.softcom.aisentinel.application.pii.detection.usecase.ManagePiiTypeConfigsUseCase;
import pro.softcom.aisentinel.application.pii.export.DetectionReportMapper;
import pro.softcom.aisentinel.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadExportContextPort;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.aisentinel.application.pii.export.port.out.WriteDetectionReportPort;
import pro.softcom.aisentinel.application.pii.export.usecase.ExportDetectionReportUseCase;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.in.*;
import pro.softcom.aisentinel.application.pii.reporting.port.out.*;
import pro.softcom.aisentinel.application.pii.reporting.service.*;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.ContentParserFactory;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.HtmlContentParser;
import pro.softcom.aisentinel.application.pii.reporting.service.parser.PlainTextParser;
import pro.softcom.aisentinel.application.pii.reporting.usecase.*;
import pro.softcom.aisentinel.application.pii.scan.port.out.LoadContentPort;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.application.pii.security.PiiAccessAuditService;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;
import pro.softcom.aisentinel.domain.pii.export.SourceType;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConfigUpdatedEvent;

import java.util.Map;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.DelegatingJiraClient;
import pro.softcom.aisentinel.infrastructure.pii.export.adapter.out.ConfluenceExportContextAdapter;
import pro.softcom.aisentinel.infrastructure.pii.export.adapter.out.DelegatingExportContextAdapter;
import pro.softcom.aisentinel.infrastructure.pii.export.adapter.out.JiraExportContextAdapter;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.JiraCloudHttpClientAdapter;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.JiraDataCenterHttpClientAdapter;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.config.DatabaseBackedJiraConnectionConfig;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.config.JiraConnectionConfig;

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
                                             JiraUrlProvider jiraUrlProvider,
                                             PiiContextExtractor piiContextExtractor,
                                             SeverityCalculationService severityCalculationService) {
        return new ScanEventFactory(confluenceUrlProvider, jiraUrlProvider, piiContextExtractor, severityCalculationService);
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
                                                    ScanSeverityCountService scanSeverityCountService) {
        return new ContentScanOrchestrator(
                scanEventFactory, 
                scanProgressCalculator, 
                scanCheckpointService, 
                scanEventStore, 
                scanEventDispatcher,
                severityCalculationService,
                scanSeverityCountService
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
            ScanTimeOutConfig scanTimeoutConfig,
            HtmlContentParser htmlContentParser,
            PersonallyIdentifiableInformationScanExecutionOrchestratorPort personallyIdentifiableInformationScanExecutionOrchestratorPort,
            ScanCheckpointRepository scanCheckpointRepository) {
        return new StreamConfluenceScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanTimeoutConfig,
                htmlContentParser,
                personallyIdentifiableInformationScanExecutionOrchestratorPort
        );
    }

    @Bean
    public StreamConfluenceResumeScanPort streamConfluenceResumeScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanCheckpointRepository scanCheckpointRepository,
            ScanTimeOutConfig scanTimeoutConfig,
            HtmlContentParser htmlContentParser) {
        return new StreamConfluenceResumeScanUseCase(
                confluenceAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                attachmentProcessor,
                scanCheckpointRepository,
                scanTimeoutConfig,
                htmlContentParser
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
    public ConfluenceExportContextAdapter confluenceExportContextAdapter(
            ConfluenceSpaceRepository confluenceSpaceRepository,
            ConfluenceClient confluenceClient) {
        return new ConfluenceExportContextAdapter(confluenceSpaceRepository, confluenceClient);
    }

    @Bean
    public JiraExportContextAdapter jiraExportContextAdapter(
            JiraClient jiraClient,
            JiraUrlProvider jiraUrlProvider) {
        return new JiraExportContextAdapter(jiraClient, jiraUrlProvider);
    }

    @Bean
    public ReadExportContextPort readExportContextPort(
            ConfluenceExportContextAdapter confluenceAdapter,
            JiraExportContextAdapter jiraAdapter) {
        return new DelegatingExportContextAdapter(Map.of(
                SourceType.CONFLUENCE, confluenceAdapter,
                SourceType.JIRA, jiraAdapter
        ));
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

    @Bean
    public ManagePiiDetectionConfigPort managePiiDetectionConfigPort(PiiDetectionConfigRepository piiDetectionConfigRepository) {
        return new ManagePiiDetectionConfigUseCase(piiDetectionConfigRepository);
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

    @Bean
    public StreamDatabaseScanPort streamDatabaseScanUseCase(
            LoadContentPort loadContentPort,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            ScanTimeOutConfig scanTimeOutConfig) {
        return new StreamDatabaseScanUseCase(
                loadContentPort,
                piiDetectorClient,
                contentScanOrchestrator,
                scanTimeOutConfig
        );
    }

    // ---- Jira beans ----

    @Bean
    public AdfContentParser adfContentParser() {
        return new AdfContentParser();
    }

    @Bean
    public JiraCloudHttpClientAdapter jiraCloudAdapter(
            @Qualifier("jiraConfig") JiraConnectionConfig config,
            ObjectMapper objectMapper,
            AdfContentParser adfContentParser) {
        return new JiraCloudHttpClientAdapter(config, objectMapper, adfContentParser);
    }

    @Bean
    public JiraDataCenterHttpClientAdapter jiraDataCenterAdapter(
            @Qualifier("jiraConfig") JiraConnectionConfig config,
            ObjectMapper objectMapper) {
        return new JiraDataCenterHttpClientAdapter(config, objectMapper);
    }

    @Bean
    public JiraClient jiraClient(
            @Qualifier("jiraConfig") JiraConnectionConfig config,
            JiraCloudHttpClientAdapter cloudAdapter,
            JiraDataCenterHttpClientAdapter dataCenterAdapter) {
        return new DelegatingJiraClient(config, cloudAdapter, dataCenterAdapter);
    }

    @Bean
    public JiraAccessor jiraAccessor(JiraClient jiraClient) {
        return new JiraAccessor(jiraClient);
    }

    @Bean
    public JiraProjectPort jiraProjectPort(JiraAccessor jiraAccessor) {
        return new FetchJiraProjectUseCase(jiraAccessor);
    }

    @Bean
    public ManageJiraConnectionPort manageJiraConnectionPort(
            JiraConnectionConfigRepository jiraConnectionConfigRepository,
            EncryptionService encryptionService,
            @Qualifier("jiraConfig") DatabaseBackedJiraConnectionConfig jiraConfig) {
        return new ManageJiraConnectionUseCase(
                jiraConnectionConfigRepository,
                encryptionService,
                jiraConfig::invalidateCache
        );
    }

    @Bean
    public StreamJiraScanPort streamJiraScanPort(
            JiraAccessor jiraAccessor,
            PiiDetectorClient piiDetectorClient,
            ContentScanOrchestrator contentScanOrchestrator,
            PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestrator) {
        return new StreamJiraScanUseCase(
                jiraAccessor,
                piiDetectorClient,
                contentScanOrchestrator,
                scanExecutionOrchestrator
        );
    }
}
