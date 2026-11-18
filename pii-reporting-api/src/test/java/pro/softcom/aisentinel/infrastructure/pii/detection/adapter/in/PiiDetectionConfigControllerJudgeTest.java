package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort.UpdatePiiDetectionConfigCommand;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PiiDetectionConfigController.class)
@Import(SecurityConfig.class)
class PiiDetectionConfigControllerJudgeTest {

    private static final String URL = "/api/v1/pii-detection/config";
    private static final String PAYLOAD = """
            {"presidioEnabled":true,"regexEnabled":false,"ministralEnabled":false,
             "ministralChunkSize":2048,"ministralOverlap":410,"defaultThreshold":0.3,
             "postfilterEnabled":false,"lmStudioHost":"localhost","lmStudioPort":1234,
             "ministralConcurrency":1,"ministralConcurrencyAuto":false,"lmStudioJudgeModel":"manual-model"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePiiDetectionConfigPort port;

    @ParameterizedTest
    @ValueSource(strings = {"LM_STUDIO", "OMLX", "STRATA"})
    void Should_SaveJudgeConfiguration_When_ProviderAndEndpointAreComplete(String provider) throws Exception {
        when(port.updateConfig(any())).thenReturn(judgeConfig(provider));
        String payload = withJudgeFields("\"judgeProvider\":\"" + provider
                + "\",\"judgeHost\":\"omlx\",\"judgePort\":8000");

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgeProvider").value(provider))
                .andExpect(jsonPath("$.judgeHost").value("omlx"))
                .andExpect(jsonPath("$.judgePort").value(8000));

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> command =
                ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(port).updateConfig(command.capture());
        assertThat(command.getValue().judgeProvider()).isEqualTo(provider);
        assertThat(command.getValue().judgeHost()).isEqualTo("omlx");
        assertThat(command.getValue().judgePort()).isEqualTo(8000);
        assertThat(command.getValue().lmStudioJudgeModel()).isEqualTo("manual-model");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"judgeProvider\":\"OMLX\"",
            "\"judgeProvider\":\"OMLX\",\"judgeHost\":\"omlx\"",
            "\"judgeProvider\":\"OMLX\",\"judgeHost\":\" \",\"judgePort\":8000",
            "\"judgeProvider\":\"UNKNOWN\",\"judgeHost\":\"omlx\",\"judgePort\":8000",
            "\"judgePort\":0",
            "\"judgePort\":65536"
    })
    void Should_RejectJudgeConfiguration_When_Invalid(String fields) throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(withJudgeFields(fields)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(port);
    }

    @Test
    void Should_ReturnRawNulls_When_UsingLegacyConfiguration() throws Exception {
        when(port.getConfig()).thenReturn(judgeConfig(null));

        mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.judgeProvider").value(nullValue()))
                .andExpect(jsonPath("$.judgeHost").value(nullValue()))
                .andExpect(jsonPath("$.judgePort").value(nullValue()));
    }

    private String withJudgeFields(String fields) {
        return PAYLOAD.replace("}", "," + fields + "}");
    }

    private PiiDetectionConfig judgeConfig(String provider) {
        return new PiiDetectionConfig(1, true, false, false, 2048, 410,
                new BigDecimal("0.30"), false, "localhost", 1234, null, "manual-model",
                1, false, null, LocalDateTime.now(), "admin", false, true, true, true,
                provider, provider == null ? null : "omlx", provider == null ? null : 8000, null);
    }
}
