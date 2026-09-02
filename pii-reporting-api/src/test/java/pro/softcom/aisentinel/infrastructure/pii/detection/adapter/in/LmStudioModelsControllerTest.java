package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.pii.detection.port.in.ListLmStudioModelsPort;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModel;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LmStudioModelsController.class)
@Import(SecurityConfig.class)
class LmStudioModelsControllerTest {

    private static final String MODELS_URL = "/api/v1/pii-detection/lm-studio/models";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListLmStudioModelsPort listLmStudioModelsPort;

    @Test
    void Should_ReturnModelsJson_When_GetModelsWithEndpoint() throws Exception {
        when(listLmStudioModelsPort.listModels("lmstudio", 4321)).thenReturn(new LmStudioModelListing(
            "http://lmstudio:4321/v1", "ministral-3b-pii-preview",
            List.of(new LmStudioModel("ministral-3b-pii-preview@q8_0", "Q8_0", "mradermacher", true)),
            ""));

        mockMvc.perform(get(MODELS_URL).param("host", "lmstudio").param("port", "4321"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endpoint").value("http://lmstudio:4321/v1"))
            .andExpect(jsonPath("$.family").value("ministral-3b-pii-preview"))
            .andExpect(jsonPath("$.models[0].id").value("ministral-3b-pii-preview@q8_0"))
            .andExpect(jsonPath("$.models[0].quantization").value("Q8_0"))
            .andExpect(jsonPath("$.models[0].loaded").value(true))
            .andExpect(jsonPath("$.error").value(""));
    }

    @Test
    void Should_UseConfiguredEndpoint_When_NoParamsGiven() throws Exception {
        when(listLmStudioModelsPort.listModels(null, null)).thenReturn(
            new LmStudioModelListing("http://localhost:1234/v1", "ministral-3b-pii-preview", List.of(),
                                     "LM Studio model list unavailable"));

        mockMvc.perform(get(MODELS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.models").isEmpty())
            .andExpect(jsonPath("$.error").value("LM Studio model list unavailable"));

        verify(listLmStudioModelsPort).listModels(null, null);
    }
}
