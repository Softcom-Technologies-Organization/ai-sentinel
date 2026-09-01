package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.in;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManageConcurrencyBenchmarkPort;
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;
import pro.softcom.aisentinel.infrastructure.config.SecurityConfig;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the ConcurrencyBenchmarkController class.
 * Verifies REST mapping for the on-demand benchmark trigger and status polling.
 */
@WebMvcTest(ConcurrencyBenchmarkController.class)
@Import(SecurityConfig.class)
class ConcurrencyBenchmarkControllerTest {

    private static final String RUN_URL = "/api/v1/pii-detection/concurrency-benchmark/run";
    private static final String STATUS_URL = "/api/v1/pii-detection/concurrency-benchmark/status";
    private static final String CANCEL_URL = "/api/v1/pii-detection/concurrency-benchmark/cancel";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageConcurrencyBenchmarkPort manageConcurrencyBenchmarkPort;

    @Test
    void Should_Return202WithPendingStatus_When_PostRun() throws Exception {
        when(manageConcurrencyBenchmarkPort.getBenchStatus())
            .thenReturn(new ConcurrencyBenchStatus("PENDING", 0, null, 1, null, 4));

        mockMvc.perform(post(RUN_URL))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.progress").value(0))
            .andExpect(jsonPath("$.message").value(nullValue()))
            .andExpect(jsonPath("$.concurrency").value(1))
            .andExpect(jsonPath("$.tunedSignature").value(nullValue()))
            .andExpect(jsonPath("$.maxConcurrency").value(4));

        // No body: the service default upper bound applies.
        verify(manageConcurrencyBenchmarkPort).requestBenchmark(4);
    }

    @Test
    void Should_ForwardRequestedMaxConcurrency_When_PostRunWithBody() throws Exception {
        when(manageConcurrencyBenchmarkPort.getBenchStatus())
            .thenReturn(new ConcurrencyBenchStatus("PENDING", 0, null, 1, null, 12));

        mockMvc.perform(post(RUN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxConcurrency\": 12}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.maxConcurrency").value(12));

        verify(manageConcurrencyBenchmarkPort).requestBenchmark(12);
    }

    @Test
    void Should_Return400_When_MaxConcurrencyOutOfRange() throws Exception {
        mockMvc.perform(post(RUN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxConcurrency\": 21}"))
            .andExpect(status().isBadRequest());

        verify(manageConcurrencyBenchmarkPort, never()).requestBenchmark(anyInt());
    }

    @Test
    void Should_Return202WithStatus_When_PostCancel() throws Exception {
        when(manageConcurrencyBenchmarkPort.getBenchStatus())
            .thenReturn(new ConcurrencyBenchStatus("CANCEL_REQUESTED", 35, "Testing concurrency 3/8", 4, "sig", 8));

        mockMvc.perform(post(CANCEL_URL))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"))
            .andExpect(jsonPath("$.maxConcurrency").value(8));

        verify(manageConcurrencyBenchmarkPort).cancelBenchmark();
    }

    @Test
    void Should_ReturnJobStatusJson_When_GetStatus() throws Exception {
        when(manageConcurrencyBenchmarkPort.getBenchStatus())
            .thenReturn(new ConcurrencyBenchStatus(
                "DONE", 100, "tuned to 4 workers", 4, "localhost:1234|ministral", 4));

        mockMvc.perform(get(STATUS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.progress").value(100))
            .andExpect(jsonPath("$.message").value("tuned to 4 workers"))
            .andExpect(jsonPath("$.concurrency").value(4))
            .andExpect(jsonPath("$.tunedSignature").value("localhost:1234|ministral"));
    }

    @Test
    void Should_Return500_When_RequestBenchmarkFails() throws Exception {
        doThrow(new RuntimeException("Database connection failed"))
            .when(manageConcurrencyBenchmarkPort).requestBenchmark(anyInt());

        mockMvc.perform(post(RUN_URL))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void Should_Return500_When_StatusRetrievalFails() throws Exception {
        when(manageConcurrencyBenchmarkPort.getBenchStatus())
            .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get(STATUS_URL))
            .andExpect(status().isInternalServerError());
    }
}
