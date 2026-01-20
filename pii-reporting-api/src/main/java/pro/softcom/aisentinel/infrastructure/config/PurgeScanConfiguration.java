package pro.softcom.aisentinel.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.application.pii.reporting.port.in.PurgeDetectionDataPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanCheckpointStore;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.application.pii.reporting.usecase.PurgeDetectionDataUseCase;

/**
 * Infrastructure configuration wiring the PurgeScanDataUseCase to its implementation.
 */
@Configuration
public class PurgeScanConfiguration {

    @Bean
    public PurgeDetectionDataPort purgeScanDataUseCase(
            ScanEventStore eventStore,
            ScanCheckpointStore checkpointStore
    ) {
        return new PurgeDetectionDataUseCase(eventStore, checkpointStore);
    }
}
