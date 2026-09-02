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
 * REST mapping tests for the {@code ministralConcurrency} /
 * {@code ministralConcurrencyAuto} / {@code ministralConcurrencyTunedSignature}
 * fields on the singleton detection config. Verifies GET exposes the fields,
 * PUT persists them through to the use-case command, and PUT rejects a
 * concurrency value outside the 1-20 range.
 */
@WebMvcTest(PiiDetectionConfigController.class)
@Import(SecurityConfig.class)
class PiiDetectionConfigControllerConcurrencyTest {

    private static final String CONFIG_URL = "/api/v1/pii-detection/config";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePiiDetectionConfigPort managePiiDetectionConfigPort;

    @Test
    void Should_ExposeMinistralConcurrencyFields_When_GetConfig() throws Exception {
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, null,
            4, false, "localhost:1234|ministral",
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.getConfig()).thenReturn(config);

        mockMvc.perform(get(CONFIG_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ministralConcurrency").value(4))
            .andExpect(jsonPath("$.ministralConcurrencyAuto").value(false))
            .andExpect(jsonPath("$.ministralConcurrencyTunedSignature").value("localhost:1234|ministral"));
    }

    @Test
    void Should_ExposeNullTunedSignature_When_NeverTuned() throws Exception {
        PiiDetectionConfig config = new PiiDetectionConfig(
            1, true, true, false, 1024, 128, new BigDecimal("0.75"), false, "localhost", 1234, null,
            1, true, null,
            LocalDateTime.now(), "admin"
        );
        when(managePiiDetectionConfigPort.getConfig()).thenReturn(config);

        mockMvc.perform(get(CONFIG_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ministralConcurrency").value(1))
            .andExpect(jsonPath("$.ministralConcurrencyAuto").value(true))
            .andExpect(jsonPath("$.ministralConcurrencyTunedSignature").doesNotExist());
    }

    @Test
    void Should_PersistMinistralConcurrencyFields_When_PutConfig() throws Exception {
        PiiDetectionConfig persisted = new PiiDetectionConfig(
            1, true, true, true, 2048, 256, new BigDecimal("0.75"), false, "localhost", 1234, null,
            8, false, "localhost:1234|ministral",
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
                  "ministralConcurrency": 8,
                  "ministralConcurrencyAuto": false,
                  "ministralConcurrencyTunedSignature": "localhost:1234|ministral"
                }
                """;

        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ministralConcurrency").value(8))
            .andExpect(jsonPath("$.ministralConcurrencyAuto").value(false))
            .andExpect(jsonPath("$.ministralConcurrencyTunedSignature").value("localhost:1234|ministral"));

        ArgumentCaptor<UpdatePiiDetectionConfigCommand> captor =
            ArgumentCaptor.forClass(UpdatePiiDetectionConfigCommand.class);
        verify(managePiiDetectionConfigPort).updateConfig(captor.capture());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(captor.getValue().ministralConcurrency()).isEqualTo(8);
        softly.assertThat(captor.getValue().ministralConcurrencyAuto()).isFalse();
        softly.assertThat(captor.getValue().ministralConcurrencyTunedSignature())
            .isEqualTo("localhost:1234|ministral");
        softly.assertAll();
    }

    @Test
    void Should_RejectUpdate_When_MinistralConcurrencyBelowMinimum() throws Exception {
        // ministralConcurrency is @Min(1) on the request DTO.
        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithConcurrency("0")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void Should_RejectUpdate_When_MinistralConcurrencyAboveMaximum() throws Exception {
        // ministralConcurrency is @Max(20) on the request DTO.
        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithConcurrency("21")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void Should_RejectUpdate_When_MinistralConcurrencyMissing() throws Exception {
        // ministralConcurrency is @NotNull on the request DTO.
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
                  "ministralConcurrencyAuto": true
                }
                """;

        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void Should_RejectUpdate_When_MinistralConcurrencyAutoMissing() throws Exception {
        // ministralConcurrencyAuto is @NotNull on the request DTO.
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
                  "ministralConcurrency": 1
                }
                """;

        mockMvc.perform(put(CONFIG_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    private String bodyWithConcurrency(String concurrency) {
        return """
                {
                  "presidioEnabled": true,
                  "regexEnabled": true,
                  "ministralEnabled": true,
                  "ministralChunkSize": 2048,
                  "ministralOverlap": 256,
                  "defaultThreshold": 0.75,
                  "lmStudioHost": "localhost",
                  "lmStudioPort": 1234,
                  "ministralConcurrency": %s,
                  "ministralConcurrencyAuto": true
                }
                """.formatted(concurrency);
    }
}
