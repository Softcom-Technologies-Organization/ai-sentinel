package pro.softcom.aisentinel.application.pii.detection.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiDetectionConfigRepository;
import pro.softcom.aisentinel.application.pii.scan.port.out.PiiDetectorClient;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
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

    @InjectMocks
    private ListLmStudioModelsUseCase useCase;

    private static final LmStudioModelListing LISTING = new LmStudioModelListing(
        "http://lmstudio:1234/v1", "ministral-3b-pii-preview",
        List.of(new LmStudioModel("ministral-3b-pii-preview@q4_k_m", "Q4_K_M", "mradermacher", false)),
        "");

    @Test
    void Should_QueryGivenEndpoint_When_HostAndPortProvided() {
        when(piiDetectorClient.listLmStudioModels("lmstudio", 4321)).thenReturn(LISTING);

        LmStudioModelListing result = useCase.listModels("lmstudio", 4321);

        assertThat(result).isEqualTo(LISTING);
        verify(configRepository, never()).findConfig();
    }

    @Test
    void Should_FallBackToConfiguredEndpoint_When_HostOrPortMissing() {
        when(configRepository.findConfig()).thenReturn(new PiiDetectionConfig(
            1, true, true, true, 2048, 410, new BigDecimal("0.30"), true,
            "10.10.17.145", 1234, "ministral-3b-pii-preview@q8_0", 4, true, null,
            LocalDateTime.now(), "system"));
        when(piiDetectorClient.listLmStudioModels("10.10.17.145", 1234)).thenReturn(LISTING);

        LmStudioModelListing result = useCase.listModels(" ", null);

        assertThat(result.models()).hasSize(1);
        verify(piiDetectorClient).listLmStudioModels("10.10.17.145", 1234);
    }

    @Test
    void Should_ReportFailure_When_ListingCarriesAnError() {
        LmStudioModelListing failed = new LmStudioModelListing("http://lmstudio:1234/v1", "", null, "unreachable");
        when(piiDetectorClient.listLmStudioModels("lmstudio", 1234)).thenReturn(failed);

        LmStudioModelListing result = useCase.listModels("lmstudio", 1234);

        assertThat(result.failed()).isTrue();
        assertThat(result.models()).isEmpty();
    }
}
