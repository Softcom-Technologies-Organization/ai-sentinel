package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.softcom.aisentinel.application.confluence.exception.ConfluenceUnreachableException;
import pro.softcom.aisentinel.application.pii.reporting.service.ScanErrorClassifier;
import pro.softcom.aisentinel.domain.pii.scan.ScanErrorKind;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRetryExecutorTest {

    private static final HttpRequest REQUEST = HttpRequest.newBuilder()
        .uri(URI.create("https://confluence.example.org/wiki/api/v2/spaces?limit=250"))
        .build();

    @Mock
    private HttpClient httpClient;

    @Test
    @DisplayName("a transport failure names the instance the call was aimed at")
    void Should_NameTheTargetInstance_When_TheCallNeverReachesConfluence() {
        givenSendFailsWith(new IOException("Tunnel failed, got: 502"));

        assertThatThrownBy(() -> new HttpRetryExecutor(httpClient, 0).executeRequest(REQUEST).get())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(ConfluenceUnreachableException.class)
            .hasMessage("Confluence unreachable at https://confluence.example.org: Tunnel failed, got: 502");
    }

    @Test
    @DisplayName("the original transport failure stays classifiable as a network outage")
    void Should_KeepTheScanAbleToClassifyTheOutage_When_TheFailureIsWrapped() {
        givenSendFailsWith(new IOException("Tunnel failed, got: 502"));

        Throwable wrapped = catchTransportFailure();

        assertThat(ScanErrorClassifier.classify(wrapped)).isEqualTo(ScanErrorKind.NETWORK_UNAVAILABLE);
    }

    @Test
    @DisplayName("a failure carrying no message falls back to its type")
    void Should_ReportTheFailureType_When_TheExceptionHasNoMessage() {
        givenSendFailsWith(new ConnectException());

        assertThat(catchTransportFailure())
            .hasMessage("Confluence unreachable at https://confluence.example.org: ConnectException");
    }

    @Test
    @DisplayName("a successful response is passed through untouched")
    @SuppressWarnings("unchecked")
    void Should_ReturnTheResponse_When_TheCallSucceeds() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(response));

        assertThat(new HttpRetryExecutor(httpClient, 0).executeRequest(REQUEST).get()).isSameAs(response);
    }

    @SuppressWarnings("unchecked")
    private void givenSendFailsWith(Throwable failure) {
        when(httpClient.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.failedFuture(failure));
    }

    private Throwable catchTransportFailure() {
        try {
            new HttpRetryExecutor(httpClient, 0).executeRequest(REQUEST).get();
            throw new AssertionError("the request was expected to fail");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (ExecutionException failed) {
            return failed.getCause();
        }
    }
}
