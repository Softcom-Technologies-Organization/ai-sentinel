package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Exécuteur HTTP avec mécanisme de retry automatique.
 * Gère les tentatives de reconnexion avec backoff exponentiel.
 */
@Slf4j
public class HttpRetryExecutor {

    private final HttpClient httpClient;
    private final int maxRetries;

    public HttpRetryExecutor(HttpClient httpClient, int maxRetries) {
        this.httpClient = httpClient;
        this.maxRetries = maxRetries;
    }

    /**
     * Exécute une requête HTTP avec retry automatique.
     */
    public CompletableFuture<HttpResponse<String>> executeRequest(HttpRequest request) {
        return executeRequestWithRetry(request, maxRetries);
    }

    private CompletableFuture<HttpResponse<String>> executeRequestWithRetry(HttpRequest request, int retriesLeft) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> retryIfNeeded(response, request, retriesLeft));
    }

    private CompletableFuture<HttpResponse<String>> retryIfNeeded(
        HttpResponse<String> response, HttpRequest request, int retriesLeft) {
        
        if (shouldRetryRequest(response.statusCode()) && retriesLeft > 0) {
            return retryAfterDelay(request, retriesLeft);
        }
        return CompletableFuture.completedFuture(response);
    }

    private boolean shouldRetryRequest(int statusCode) {
        return statusCode >= 500 || statusCode == 429;
    }

    private CompletableFuture<HttpResponse<String>> retryAfterDelay(HttpRequest request, int retriesLeft) {
        var delay = calculateRetryDelay(maxRetries - retriesLeft);
        log.warn("Retry de la requête après {} ms, tentatives restantes: {}", delay, retriesLeft);
        
        var delayedExecutor = CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS);
        return CompletableFuture.runAsync(() -> {}, delayedExecutor)
            .thenCompose(_ -> executeRequestWithRetry(request, retriesLeft - 1));
    }

    private long calculateRetryDelay(int attemptNumber) {
        return (long) (Math.pow(2, attemptNumber) * 1000);
    }
}
