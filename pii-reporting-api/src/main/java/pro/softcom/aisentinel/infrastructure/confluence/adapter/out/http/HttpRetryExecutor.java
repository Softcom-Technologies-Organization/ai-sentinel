package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.http;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * Exécute une requête HTTP avec retry automatique (réponse String).
     */
    public CompletableFuture<HttpResponse<String>> executeRequest(HttpRequest request) {
        return executeRequest(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Exécute une requête HTTP avec retry automatique et body handler personnalisé.
     */
    public <T> CompletableFuture<HttpResponse<T>> executeRequest(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        return executeRequestWithRetry(request, bodyHandler, maxRetries);
    }

    private <T> CompletableFuture<HttpResponse<T>> executeRequestWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, int retriesLeft) {
        return httpClient.sendAsync(request, bodyHandler)
            .thenCompose(response -> retryIfNeeded(response, request, bodyHandler, retriesLeft))
            .exceptionallyCompose(ex -> {
                if (retriesLeft > 0) {
                    log.warn("Network error, retrying ({} left): {}", retriesLeft, ex.getMessage());
                    return retryAfterDelay(request, bodyHandler, retriesLeft);
                }
                return CompletableFuture.failedFuture(ex);
            });
    }

    private <T> CompletableFuture<HttpResponse<T>> retryIfNeeded(
        HttpResponse<T> response, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, int retriesLeft) {

        if (shouldRetryRequest(response.statusCode()) && retriesLeft > 0) {
            return retryAfterDelay(request, bodyHandler, retriesLeft);
        }
        return CompletableFuture.completedFuture(response);
    }

    private boolean shouldRetryRequest(int statusCode) {
        return statusCode >= 500 || statusCode == 429;
    }

    private <T> CompletableFuture<HttpResponse<T>> retryAfterDelay(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, int retriesLeft) {
        var delay = calculateRetryDelay(maxRetries - retriesLeft);
        log.warn("Retry de la requête après {} ms, tentatives restantes: {}", delay, retriesLeft);

        var delayedExecutor = CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS);
        return CompletableFuture.runAsync(() -> {}, delayedExecutor)
            .thenCompose(ignored -> executeRequestWithRetry(request, bodyHandler, retriesLeft - 1));
    }

    private long calculateRetryDelay(int attemptNumber) {
        return (long) (Math.pow(2, attemptNumber) * 1000);
    }
}
