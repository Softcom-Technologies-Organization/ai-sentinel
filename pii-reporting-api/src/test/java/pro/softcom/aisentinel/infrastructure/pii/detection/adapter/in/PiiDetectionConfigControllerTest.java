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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the PiiDetectionConfigController class.
 * Verifies REST mapping for the {@code postfilterEnabled} flag.
 */
@WebMvcTest(PiiDetectionConfigController.class)
@Import(SecurityConfig.class)
class PiiDetectionConfigControllerTest {

    private static final String CONFIG_URL = "/api/v1/pii-detection/config";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePiiDetectionConfigPort managePiiDetectionConfigPort;

    @Test
    void Should_ReturnPostfilterEnabledInResponse_When_GetConfig() throws Exception {
        PiiDetectionConfig domainConfig = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.getConfig()).thenReturn(domainConfig);

        mockMvc.perform(get(CONFIG_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postfilterEnabled").value(true));
    }

    @Test
    void Should_UpdatePostfilterEnabled_When_PutRequestEnablesFlag() throws Exception {
        PiiDetectionConfig persisted = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), true, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.updateConfig(any(UpdatePiiDetectionConfigCommand.class)))
            .thenReturn(persisted);

        String body = """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
                  "ministralEnabled": false,
                  "ministralChunkSize": 1024,
                  "ministralOverlap": 128,
                  "defaultThreshold": 0.75,
                  "postfilterEnabled": true,
                  "lmStudioHost": "localhost",
                  "lmStudioPort": 1234,
                  "ministralConcurrency": 1,
                  "ministralConcurrencyAuto": true
                }
                """;

        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postfilterEnabled").value(true));

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> captor =
            ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(managePiiDetectionConfigPort).updateConfig(captor.capture());
        assertThat(captor.getValue().postfilterEnabled()).isTrue();
    }

    @Test
    void Should_DefaultPostfilterEnabledToFalse_When_OmittedInUpdateRequest() throws Exception {
        PiiDetectionConfig persisted = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.updateConfig(any(UpdatePiiDetectionConfigCommand.class)))
            .thenReturn(persisted);

        // No postfilterEnabled field in payload → must default to false at command-build time.
        String body = """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
                  "ministralEnabled": false,
                  "ministralChunkSize": 1024,
                  "ministralOverlap": 128,
                  "defaultThreshold": 0.75,
                  "lmStudioHost": "localhost",
                  "lmStudioPort": 1234,
                  "ministralConcurrency": 1,
                  "ministralConcurrencyAuto": true
                }
                """;

        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postfilterEnabled").value(false));

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> captor =
            ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(managePiiDetectionConfigPort).updateConfig(captor.capture());
        assertThat(captor.getValue().postfilterEnabled()).isFalse();
    }
}
