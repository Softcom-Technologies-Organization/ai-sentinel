package pro.softcom.sentinelle.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PurgeDetectionDataUseCase;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanCheckpointStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.sentinelle.application.pii.reporting.usecase.PurgeDetectionDataUseCaseImpl;

/**
 * Infrastructure configuration wiring the PurgeScanDataUseCase to its implementation.
 */
@Configuration
public class PurgeScanConfiguration {

    @Bean
    public PurgeDetectionDataUseCase purgeScanDataUseCase(
            ScanEventStore eventStore,
            ScanCheckpointStore checkpointStore
    ) {
        return new PurgeDetectionDataUseCaseImpl(eventStore, checkpointStore);
    }
}
