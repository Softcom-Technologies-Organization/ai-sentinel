package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.domain.pii.judge.JudgeEndpoint;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModel;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListLmStudioModelsUseCaseTest {

    @Mock
    private PiiDetectorClient piiDetectorClient;

    @Mock
    private PiiDetectionConfigRepository configRepository;

    private ListLmStudioModelsUseCase useCase() {
        return new ListLmStudioModelsUseCase(piiDetectorClient, configRepository, new JudgeEndpoint(null, null));
    }

    private static final LmStudioModelListing LISTING = new LmStudioModelListing(
        "http://lmstudio:1234/v1", "ministral-3b-pii-preview",
        List.of(new LmStudioModel("ministral-3b-pii-preview@q4_k_m", "Q4_K_M", "mradermacher", false)),
        "");

    @Test
    void Should_QueryGivenEndpoint_When_HostAndPortProvided() {
        when(piiDetectorClient.listLmStudioModels("lmstudio", 4321, false)).thenReturn(LISTING);

        LmStudioModelListing result = useCase().listModels("lmstudio", 4321, false);

        assertThat(result).isEqualTo(LISTING);
        verify(configRepository, never()).findConfig();
    }

    @Test
    void Should_FallBackToConfiguredEndpoint_When_HostOrPortMissing() {
        when(configRepository.findConfig()).thenReturn(new PiiDetectionConfig(
            1, true, true, true, 2048, 410, new BigDecimal("0.30"), true,
            "10.10.17.145", 1234, "ministral-3b-pii-preview@q8_0", null, 4, true, null,
            LocalDateTime.now(), "system", false, true, true, true, null, null, null, null));
        when(piiDetectorClient.listLmStudioModels("10.10.17.145", 1234, false)).thenReturn(LISTING);

        LmStudioModelListing result = useCase().listModels(" ", null, false);

        assertThat(result.models()).hasSize(1);
        verify(piiDetectorClient).listLmStudioModels("10.10.17.145", 1234, false);
    }

    @Test
    void Should_ReportFailure_When_ListingCarriesAnError() {
        LmStudioModelListing failed = new LmStudioModelListing("http://lmstudio:1234/v1", "", null, "unreachable");
        when(piiDetectorClient.listLmStudioModels("lmstudio", 1234, false)).thenReturn(failed);

        LmStudioModelListing result = useCase().listModels("lmstudio", 1234, false);

        assertThat(result.failed()).isTrue();
        assertThat(result.models()).isEmpty();
    }

    @Test
    void Should_PreservePreviewEndpoint_When_JudgeEnvironmentIsConfigured() {
        var useCase = new ListLmStudioModelsUseCase(piiDetectorClient, configRepository,
                new JudgeEndpoint("strata", 8081));
        when(piiDetectorClient.listLmStudioModels("preview", 8000, true)).thenReturn(LISTING);

        assertThat(useCase.listModels("preview", 8000, true)).isEqualTo(LISTING);

        verify(configRepository, never()).findConfig();
    }

    @Test
    void Should_KeepDetectionEndpoint_When_OnlyDetectionModelsRequested() {
        var useCase = new ListLmStudioModelsUseCase(piiDetectorClient, configRepository,
                new JudgeEndpoint("strata", 8081));
        when(piiDetectorClient.listLmStudioModels("ministral", 1234, false)).thenReturn(LISTING);

        assertThat(useCase.listModels("ministral", 1234, false)).isEqualTo(LISTING);
    }

    @Test
    void Should_QuerySavedJudge_When_PreviewIsAbsent() {
        when(configRepository.findConfig()).thenReturn(judgeConfig("OMLX", "omlx", 8000));
        when(piiDetectorClient.listLmStudioModels("omlx", 8000, true)).thenReturn(LISTING);
        var useCase = new ListLmStudioModelsUseCase(piiDetectorClient, configRepository,
                new JudgeEndpoint("environment", 8080));

        assertThat(useCase.listModels(null, null, true)).isEqualTo(LISTING);
    }

    @Test
    void Should_QueryEnvironment_When_SavedJudgeIsAbsent() {
        when(configRepository.findConfig()).thenReturn(judgeConfig(null, null, null));
        when(piiDetectorClient.listLmStudioModels("environment", 8080, true)).thenReturn(LISTING);
        var useCase = new ListLmStudioModelsUseCase(piiDetectorClient, configRepository,
                new JudgeEndpoint("environment", 8080));

        assertThat(useCase.listModels(null, null, true)).isEqualTo(LISTING);
    }

    @Test
    void Should_FillOnlyMissingPreviewValue_When_JudgeIsSaved() {
        when(configRepository.findConfig()).thenReturn(judgeConfig("OMLX", "omlx", 8000));
        when(piiDetectorClient.listLmStudioModels("preview", 8000, true)).thenReturn(LISTING);

        assertThat(useCase().listModels("preview", null, true)).isEqualTo(LISTING);
    }

    @Test
    void Should_IgnoreSavedJudge_When_ListingDetectionModels() {
        when(configRepository.findConfig()).thenReturn(judgeConfig("OMLX", "omlx", 8000));
        when(piiDetectorClient.listLmStudioModels("localhost", 1234, false)).thenReturn(LISTING);

        assertThat(useCase().listModels(null, null, false)).isEqualTo(LISTING);
    }

    private PiiDetectionConfig judgeConfig(String provider, String host, Integer port) {
        return new PiiDetectionConfig(1, true, true, false, 2048, 410,
                new BigDecimal("0.30"), false, "localhost", 1234, null, "manual-model",
                1, false, null, LocalDateTime.now(), "test", false, true, true, true,
                provider, host, port, null);
    }
}
