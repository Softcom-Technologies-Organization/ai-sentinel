package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.Test;
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
class PiiDetectionConfigControllerTrioTest {

    private static final String URL = "/api/v1/pii-detection/config";
    private static final String PAYLOAD = """
            {"presidioEnabled":false,"regexEnabled":false,"ministralEnabled":false,
             "ministralChunkSize":2048,"ministralOverlap":410,"defaultThreshold":0.3,
             "postfilterEnabled":false,"lmStudioHost":"localhost","lmStudioPort":1234,
             "ministralConcurrency":1,"ministralConcurrencyAuto":false,
             "trioEnabled":true,"bardsEnabled":false,"cbxEnabled":true,"ettinEnabled":false}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePiiDetectionConfigPort port;

    @Test
    void Should_ExposeTrioChildren_When_ConfigRead() throws Exception {
        when(port.getConfig()).thenReturn(trioConfig());

        mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.trioEnabled").value(true))
                .andExpect(jsonPath("$.bardsEnabled").value(false))
                .andExpect(jsonPath("$.cbxEnabled").value(true))
                .andExpect(jsonPath("$.ettinEnabled").value(false));
    }

    @Test
    void Should_SaveTrioOnly_When_OneChildEnabled() throws Exception {
        when(port.updateConfig(any())).thenReturn(trioConfig());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> command =
                ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(port).updateConfig(command.capture());
        assertThat(command.getValue().trioEnabled()).isTrue();
        assertThat(command.getValue().bardsEnabled()).isFalse();
        assertThat(command.getValue().cbxEnabled()).isTrue();
        assertThat(command.getValue().ettinEnabled()).isFalse();
    }

    @Test
    void Should_RejectTrio_When_AllChildrenDisabled() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD.replace("\"cbxEnabled\":true", "\"cbxEnabled\":false")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(port);
    }

    @Test
    void Should_RejectConfig_When_OnlyTrioChildrenEnabled() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD.replace("\"trioEnabled\":true", "\"trioEnabled\":false")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(port);
    }

    private PiiDetectionConfig trioConfig() {
        return new PiiDetectionConfig(1, false, false, false, 2048, 410,
                new BigDecimal("0.30"), false, "localhost", 1234, null, null,
                1, false, null, LocalDateTime.now(), "admin", true, false, true, false, null, null, null, null);
    }
}
