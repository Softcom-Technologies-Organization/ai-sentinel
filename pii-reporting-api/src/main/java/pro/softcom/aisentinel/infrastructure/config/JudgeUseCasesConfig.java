package pro.softcom.aisentinel.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.in.JudgeEvaluationPort;
import pro.softcom.aisentinel.application.pii.judge.port.in.TrackJudgeJobPort;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.judge.service.JudgeJobRunner;
import pro.softcom.aisentinel.application.pii.judge.usecase.JudgeEvaluationUseCase;
import pro.softcom.aisentinel.application.pii.judge.usecase.TrackJudgeJobUseCase;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * Spring wiring of the LLM judge use cases, keeping the application layer framework-free.
 */
@Configuration
public class JudgeUseCasesConfig {

    @Bean
    public JudgeJobRunner judgeJobRunner(ScanResultQuery scanResultQuery,
                                         ScanEventFindingResolver scanEventFindingResolver,
                                         FindingRemediationStore findingRemediationStore,
                                         JudgeJobStore judgeJobStore,
                                         PiiDetectorClient piiDetectorClient,
                                         PiiDetectionConfigRepository piiDetectionConfigRepository,
                                         PublishRemediationEventPort publishRemediationEventPort) {
        return new JudgeJobRunner(scanResultQuery, scanEventFindingResolver, findingRemediationStore,
                judgeJobStore, piiDetectorClient, piiDetectionConfigRepository, publishRemediationEventPort,
                Clock.systemUTC());
    }

    @Bean
    public JudgeEvaluationPort judgeEvaluationPort(RemediationConfigPort remediationConfigPort,
                                                   SelectionResolver selectionResolver,
                                                   JudgeJobStore judgeJobStore,
                                                   PiiDetectionConfigRepository piiDetectionConfigRepository,
                                                   JudgeJobRunner judgeJobRunner,
                                                   ScanCheckpointRepository checkpointRepository,
                                                   ScanResultQuery scanResultQuery,
                                                   PersonallyIdentifiableInformationScanExecutionOrchestratorPort scanExecutionOrchestrator,
                                                   @Qualifier("remediationJobExecutor") ExecutorService jobExecutor) {
        return new JudgeEvaluationUseCase(remediationConfigPort, selectionResolver, judgeJobStore,
                piiDetectionConfigRepository, judgeJobRunner, jobExecutor, checkpointRepository,
                scanResultQuery, scanExecutionOrchestrator, Clock.systemUTC());
    }

    @Bean
    public TrackJudgeJobPort trackJudgeJobPort(RemediationConfigPort remediationConfigPort,
                                               JudgeJobStore judgeJobStore,
                                               FindingRemediationStore findingRemediationStore) {
        return new TrackJudgeJobUseCase(remediationConfigPort, judgeJobStore, findingRemediationStore,
                Clock.systemUTC());
    }
}
