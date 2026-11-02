package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.FindingRemediationStore;
import pro.softcom.aisentinel.application.pii.remediation.port.out.PublishRemediationEventPort;
import pro.softcom.aisentinel.application.pii.remediation.port.out.RemediationConfigPort;
import pro.softcom.aisentinel.application.pii.remediation.service.ScanEventFindingResolver;
import pro.softcom.aisentinel.application.pii.remediation.service.SelectionResolver;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PersonallyIdentifiableInformationScanExecutionOrchestratorPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;
import pro.softcom.aisentinel.infrastructure.config.JudgeUseCasesConfig;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JudgeStrataConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JudgeUseCasesConfig.class)
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(ScanResultQuery.class, () -> mock(ScanResultQuery.class))
            .withBean(ScanEventFindingResolver.class, () -> mock(ScanEventFindingResolver.class))
            .withBean(FindingRemediationStore.class, () -> mock(FindingRemediationStore.class))
            .withBean(JudgeJobStore.class, () -> mock(JudgeJobStore.class))
            .withBean(PiiDetectorClient.class, () -> mock(PiiDetectorClient.class))
            .withBean(PiiDetectionConfigRepository.class, () -> mock(PiiDetectionConfigRepository.class))
            .withBean(PublishRemediationEventPort.class, () -> mock(PublishRemediationEventPort.class))
            .withBean(RemediationConfigPort.class, () -> mock(RemediationConfigPort.class))
            .withBean(SelectionResolver.class, () -> mock(SelectionResolver.class))
            .withBean(ScanCheckpointRepository.class, () -> mock(ScanCheckpointRepository.class))
            .withBean(PersonallyIdentifiableInformationScanExecutionOrchestratorPort.class,
                    () -> mock(PersonallyIdentifiableInformationScanExecutionOrchestratorPort.class))
            .withBean("remediationJobExecutor", ExecutorService.class, () -> mock(ExecutorService.class));

    @Test
    void Should_WireStrataEndpoint_When_EnvironmentOverridesProvided() {
        contextRunner.withPropertyValues("LLM_JUDGE_HOST=host.docker.internal", "LLM_JUDGE_PORT=8081")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JudgeEndpoint.class))
                            .isEqualTo(new JudgeEndpoint("host.docker.internal", 8081));
                });
    }

    @Test
    void Should_UseDetectionFallback_When_EnvironmentOverridesAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JudgeEndpoint.class)).isEqualTo(new JudgeEndpoint(null, null));
        });
    }

    @Test
    void Should_RejectConfiguration_When_JudgePortIsInvalid() {
        contextRunner.withPropertyValues("LLM_JUDGE_PORT=65536")
                .run(context -> assertThat(context).hasFailed());
    }
}
