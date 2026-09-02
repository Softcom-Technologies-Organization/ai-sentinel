package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.assertj.core.api.SoftAssertions;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST mapping tests for the {@code ministralEnabled} / {@code ministralChunkSize}
 * / {@code ministralOverlap} fields on the singleton detection config. Verifies
 * GET exposes the fields, PUT persists them through to the use-case command, and
 * PUT rejects an overlap greater than or equal to the chunk size.
 */
@WebMvcTest(PiiDetectionConfigController.class)
@Import(SecurityConfig.class)
class PiiDetectionConfigControllerMinistralTest {

    private static final String CONFIG_URL = "/api/v1/pii-detection/config";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePiiDetectionConfigPort managePiiDetectionConfigPort;

    @Test
    void Should_ExposeMinistralFields_When_GetConfig() throws Exception {
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.getConfig()).thenReturn(config);

        mockMvc.perform(get(CONFIG_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ministralEnabled").value(true))
            .andExpect(jsonPath("$.ministralChunkSize").value(2048))
            .andExpect(jsonPath("$.ministralOverlap").value(256));
    }

    @Test
    void Should_DefaultMinistralEnabledFalse_When_GetConfig() throws Exception {
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.getConfig()).thenReturn(config);

        mockMvc.perform(get(CONFIG_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ministralEnabled").value(false));
    }

    @Test
    void Should_PersistMinistralFields_When_PutConfig() throws Exception {
        PiiDetectionConfig persisted = new PiiDetectionConfig(
            1, true, true, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.updateConfig(any(UpdatePiiDetectionConfigCommand.class)))
            .thenReturn(persisted);

        String body = """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
                  "ministralEnabled": true,
                  "ministralChunkSize": 2048,
                  "ministralOverlap": 256,
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
            .andExpect(jsonPath("$.ministralEnabled").value(true))
            .andExpect(jsonPath("$.ministralChunkSize").value(2048))
            .andExpect(jsonPath("$.ministralOverlap").value(256));

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> captor =
            ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(managePiiDetectionConfigPort).updateConfig(captor.capture());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captor.getValue().ministralEnabled()).isTrue();
        softly.assertThat(captor.getValue().ministralChunkSize()).isEqualTo(2048);
        softly.assertThat(captor.getValue().ministralOverlap()).isEqualTo(256);
        softly.assertAll();
    }

    @Test
    void Should_RejectUpdate_When_MinistralOverlapNotLessThanChunkSize() throws Exception {
        // ministralOverlap >= ministralChunkSize violates the cross-field rule.
        String body = """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
                  "ministralEnabled": true,
                  "ministralChunkSize": 256,
                  "ministralOverlap": 256,
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
            .andExpect(status().isBadRequest());
    }

    @Test
    void Should_RejectUpdate_When_MinistralEnabledMissing() throws Exception {
        // ministralEnabled is @NotNull on the request DTO.
        String body = """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
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
            .andExpect(status().isBadRequest());
    }
}
