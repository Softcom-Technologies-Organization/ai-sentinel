package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.application.pii.reporting.ScanSeverityCountService;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSeverityCountRepository;

/**
 * Configuration for application layer services.
 * 
 * This configuration lives in the infrastructure layer to keep the application layer
 * independent of Spring Framework, following hexagonal architecture principles.
 */
@Configuration
public class ApplicationServicesConfiguration {

    /**
     * Creates the severity calculation service bean.
     * This service has no dependencies and contains the business rules for PII severity mapping.
     */
    @Bean
    public SeverityCalculationService severityCalculationService() {
        return new SeverityCalculationService();
    }

    /**
     * Creates the scan severity count service bean.
     * This service manages atomic operations on severity counts during scans.
     */
    @Bean
    public ScanSeverityCountService scanSeverityCountService(
            ScanSeverityCountRepository scanSeverityCountRepository) {
        return new ScanSeverityCountService(scanSeverityCountRepository);
    }
}
