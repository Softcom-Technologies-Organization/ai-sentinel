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
import pro.softcom.sentinelle.application.confluence.usecase.ConfluenceUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.port.in.DetectionReportingUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceResumeScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.in.StreamConfluenceScanUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.reporting.service.AttachmentProcessor;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanCheckpointService;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanEventFactory;
import pro.softcom.sentinelle.application.pii.reporting.service.ScanProgressCalculator;
import pro.softcom.sentinelle.application.pii.reporting.usecase.ScanResultUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceResumeScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.reporting.usecase.StreamConfluenceScanUseCaseImpl;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.sentinelle.application.pii.scan.port.out.PiiDetectorSettings;
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
    public DetectionReportingUseCase scanResultUseCase(ScanResultQuery scanResultQuery,
                                                       ScanCheckpointRepository checkpointRepo) {
        return new ScanResultUseCaseImpl(scanResultQuery, checkpointRepo);
    }

    @Bean
    public ScanProgressCalculator scanProgressCalculator() {
        return new ScanProgressCalculator();
    }

    @Bean
    public ScanEventFactory scanEventFactory(ConfluenceUrlProvider confluenceUrlProvider) {
        return new ScanEventFactory(confluenceUrlProvider);
    }

    @Bean
    public ScanCheckpointService scanCheckpointService(ScanCheckpointRepository scanCheckpointRepository) {
        return new ScanCheckpointService(scanCheckpointRepository);
    }

    @Bean
    public AttachmentProcessor attachmentProcessor(
            ConfluenceAttachmentDownloader confluenceDownloadService,
            AttachmentTextExtractor attachmentTextExtractionService,
            PiiDetectorClient piiDetectorClient,
            PiiDetectorSettings piiSettings,
            ScanEventFactory scanEventFactory,
            ScanProgressCalculator scanProgressCalculator) {
        return new AttachmentProcessor(confluenceDownloadService, attachmentTextExtractionService,
                                       piiDetectorClient, piiSettings, scanEventFactory,
                                       scanProgressCalculator);
    }

    @Bean
    public StreamConfluenceScanUseCase streamConfluenceScanUseCase(
            ConfluenceClient confluenceService,
            ConfluenceAttachmentClient confluenceAttachmentService,
            PiiDetectorSettings piiSettings,
            PiiDetectorClient piiDetectorClient,
            ScanEventStore scanEventStore,
            ScanEventFactory scanEventFactory,
            ScanProgressCalculator scanProgressCalculator,
            ScanCheckpointService scanCheckpointService,
            AttachmentProcessor attachmentProcessor) {
        return new StreamConfluenceScanUseCaseImpl(
                confluenceService,
                confluenceAttachmentService,
                piiSettings,
                piiDetectorClient,
                scanEventStore,
                scanEventFactory,
                scanProgressCalculator,
                scanCheckpointService,
                attachmentProcessor
        );
    }

    @Bean
    public StreamConfluenceResumeScanUseCase streamConfluenceResumeScanUseCase(
            ConfluenceClient confluenceService,
            ConfluenceAttachmentClient confluenceAttachmentService,
            PiiDetectorSettings piiSettings,
            PiiDetectorClient piiDetectorClient,
            ScanEventStore scanEventStore,
            ScanEventFactory scanEventFactory,
            ScanProgressCalculator scanProgressCalculator,
            ScanCheckpointService scanCheckpointService,
            AttachmentProcessor attachmentProcessor,
            ScanCheckpointRepository scanCheckpointRepository) {
        return new StreamConfluenceResumeScanUseCaseImpl(
                confluenceService,
                confluenceAttachmentService,
                piiSettings,
                piiDetectorClient,
                scanEventStore,
                scanEventFactory,
                scanProgressCalculator,
                scanCheckpointService,
                attachmentProcessor,
                scanCheckpointRepository
        );
    }
}
