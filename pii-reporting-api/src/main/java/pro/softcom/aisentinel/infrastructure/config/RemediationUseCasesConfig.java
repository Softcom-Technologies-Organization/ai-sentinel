package pro.softcom.aisentinel.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ChangeFindingStatusPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.ExecuteObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.PlanObfuscationPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.QueryRemediationFindingsPort;
import pro.softcom.aisentinel.application.pii.remediation.port.in.TrackObfuscationJobPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.ObfuscationJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.SourcePageRedactionPort;
import pro.softcom.aisentinel.application.pii.remediation.service.ObfuscationJobRunner;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionEvaluator;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.remediation.usecase.ChangeFindingStatusUseCase;
import pro.softcom.aisentinel.application.pii.remediation.usecase.ExecuteObfuscationUseCase;
import pro.softcom.aisentinel.application.pii.remediation.usecase.PlanObfuscationUseCase;
import pro.softcom.aisentinel.application.pii.remediation.usecase.QueryRemediationFindingsUseCase;
import pro.softcom.aisentinel.application.pii.remediation.usecase.TrackObfuscationJobUseCase;
import pro.softcom.aisentinel.application.pii.reporting.SeverityCalculationService;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring wiring of the PII remediation use cases, keeping the application layer
 * framework-free.
 */
@Configuration
public class RemediationUseCasesConfig {

    @Bean
    public ScanEventFindingResolver scanEventFindingResolver(SeverityCalculationService severityCalculationService) {
        return new ScanEventFindingResolver(severityCalculationService);
    }

    @Bean
    public SelectionEvaluator selectionEvaluator() {
        return new SelectionEvaluator();
    }

    @Bean
    public QueryRemediationFindingsPort queryRemediationFindingsPort(
            RemediationConfigPort remediationConfigPort,
            ScanResultQuery scanResultQuery,
            ScanCheckpointRepository checkpointRepository,
            FindingRemediationStore findingRemediationStore,
            ScanEventFindingResolver scanEventFindingResolver,
            SelectionEvaluator selectionEvaluator) {
        return new QueryRemediationFindingsUseCase(remediationConfigPort, scanResultQuery, checkpointRepository,
                findingRemediationStore, scanEventFindingResolver, selectionEvaluator);
    }

    @Bean
    public ChangeFindingStatusPort changeFindingStatusPort(
            RemediationConfigPort remediationConfigPort,
            ScanResultQuery scanResultQuery,
            ScanCheckpointRepository checkpointRepository,
            FindingRemediationStore findingRemediationStore,
            ScanEventFindingResolver scanEventFindingResolver,
            SelectionResolver selectionResolver,
            PublishRemediationEventPort publishRemediationEventPort) {
        return new ChangeFindingStatusUseCase(remediationConfigPort, scanResultQuery, checkpointRepository,
                findingRemediationStore, scanEventFindingResolver, selectionResolver,
                publishRemediationEventPort, Clock.systemUTC());
    }

    @Bean
    public SelectionResolver selectionResolver(
            ScanResultQuery scanResultQuery,
            ScanCheckpointRepository checkpointRepository,
            FindingRemediationStore findingRemediationStore,
            ScanEventFindingResolver scanEventFindingResolver,
            SelectionEvaluator selectionEvaluator) {
        return new SelectionResolver(scanResultQuery, checkpointRepository, findingRemediationStore,
                scanEventFindingResolver, selectionEvaluator);
    }

    @Bean
    public PlanObfuscationPort planObfuscationPort(
            RemediationConfigPort remediationConfigPort,
            SelectionResolver selectionResolver) {
        return new PlanObfuscationUseCase(remediationConfigPort, selectionResolver);
    }

    @Bean
    public ObfuscationJobRunner obfuscationJobRunner(
            ScanResultQuery scanResultQuery,
            FindingRemediationStore findingRemediationStore,
            ObfuscationJobStore obfuscationJobStore,
            SourcePageRedactionPort sourcePageRedactionPort) {
        return new ObfuscationJobRunner(scanResultQuery, findingRemediationStore,
                obfuscationJobStore, sourcePageRedactionPort, Clock.systemUTC());
    }

    @Bean(destroyMethod = "close")
    public ExecutorService remediationJobExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ExecuteObfuscationPort executeObfuscationPort(
            RemediationConfigPort remediationConfigPort,
            SelectionResolver selectionResolver,
            ObfuscationJobStore obfuscationJobStore,
            ObfuscationJobRunner obfuscationJobRunner,
            ExecutorService remediationJobExecutor) {
        return new ExecuteObfuscationUseCase(remediationConfigPort, selectionResolver,
                obfuscationJobStore, obfuscationJobRunner, remediationJobExecutor, Clock.systemUTC());
    }

    @Bean
    public TrackObfuscationJobPort trackObfuscationJobPort(
            RemediationConfigPort remediationConfigPort,
            ObfuscationJobStore obfuscationJobStore) {
        return new TrackObfuscationJobUseCase(remediationConfigPort, obfuscationJobStore);
    }
}
