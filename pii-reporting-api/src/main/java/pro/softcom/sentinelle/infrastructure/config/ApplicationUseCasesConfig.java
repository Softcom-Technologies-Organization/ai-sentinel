package pro.softcom.sentinelle.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.sentinelle.application.confluence.port.in.ConfluenceUseCase;
import pro.softcom.sentinelle.application.confluence.port.out.AttachmentTextExtractor;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceUrlProvider;
import pro.softcom.sentinelle.application.confluence.service.ConfluenceAccessor;
import pro.softcom.sentinelle.application.confluence.usecase.ConfluenceUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PauseScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.ScanReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.AfterCommitExecutionPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventDispatcher;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanOrchestrator;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.sentinelle.application.pii.reporting.usecase.PauseScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.ScanReportingUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceResumeScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;

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
            AttachmentProcessor attachmentProcessor) {
        return new StreamConfluenceScanUseCaseImpl(
                confluenceAccessor,
                piiDetectorClient,
                scanOrchestrator,
                attachmentProcessor
        );
    }

    @Bean
    public StreamConfluenceResumeScanUseCase streamConfluenceResumeScanUseCase(
            ConfluenceAccessor confluenceAccessor,
            PiiDetectorClient piiDetectorClient,
            ScanOrchestrator scanOrchestrator,
            AttachmentProcessor attachmentProcessor,
            ScanCheckpointRepository scanCheckpointRepository) {
        return new StreamConfluenceResumeScanUseCaseImpl(
                confluenceAccessor,
                piiDetectorClient,
                scanOrchestrator,
                attachmentProcessor,
                scanCheckpointRepository
        );
    }

    @Bean
    public PauseScanUseCase pauseScanUseCase(ScanCheckpointRepository scanCheckpointRepository) {
        return new PauseScanUseCaseImpl(scanCheckpointRepository);
    }
}
