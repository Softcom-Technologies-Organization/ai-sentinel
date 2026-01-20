package pro.softcom.aisentinel.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceAttachmentDownloader;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ConfluenceAttachmentHttpDownloaderAdapter implements ConfluenceAttachmentDownloader {

    private final ConfluenceConnectionConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authHeader;

    private static final String RESULTS_FIELD = "results";
    private static final String TITLE_FIELD = "title";
    public static final String ACCEPT_HEADER_NAME = "Accept";
    public static final String CONTENT_TYPE_HEADER_VALUE = "application/json";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    public ConfluenceAttachmentHttpDownloaderAdapter(@Qualifier("confluenceConfig") ConfluenceConnectionConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        var executor = Executors.newVirtualThreadPerTaskExecutor();

        this.httpClient = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofMillis(config.connectTimeout()))
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.authHeader = createAuthHeader(config.username(), config.apiToken());
    }

    private String createAuthHeader(String username, String apiToken) {
        var credentials = username + ":" + apiToken;
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }

    public CompletableFuture<Optional<byte[]>> downloadAttachmentContent(String pageId, String attachmentTitle) {
        long time = System.currentTimeMillis();
        if (pageId == null || pageId.isBlank() || attachmentTitle == null || attachmentTitle.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        log.info("Téléchargement pièce jointe '{}' pour la page {}", attachmentTitle, pageId);
        String uriStr = config.getRestApiUrl() + config.contentPath() + pageId + config.attachmentChildSuffix() + "?limit=200&expand=results._links";
        var listReq = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .header(AUTHORIZATION_HEADER_NAME, authHeader)
                .header(ACCEPT_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE)
                .timeout(Duration.ofMillis(config.readTimeout()))
                .GET()
                .build();

        // For listing attachments here, do not use retry to align with expected behavior:
        // one-shot listing; retry will be applied only to the bytes download step.
        return httpClient.sendAsync(listReq, HttpResponse.BodyHandlers.ofString()).thenCompose(resp -> {
            if (resp.statusCode() != 200) {
                log.warn("Impossible de lister les pièces jointes (status={}) pageId={}", resp.statusCode(), pageId);
                return CompletableFuture.completedFuture(Optional.empty());
            }
            try {
                long t1 = System.currentTimeMillis();
                JsonNode root = objectMapper.readTree(resp.body());
                log.info("jsonNode read in {} ms", System.currentTimeMillis() - t1);
                JsonNode results = root.get(RESULTS_FIELD);
                if (results == null || !results.isArray()) {
                    return CompletableFuture.completedFuture(Optional.empty());
                }
                final CompletableFuture<Optional<byte[]>> optionalCompletableFuture = processDownloadFromResults(pageId, attachmentTitle, results);
                log.info("downloadAttachmentContent took {} ms", System.currentTimeMillis() - time);
                return optionalCompletableFuture;
            } catch (Exception e) {
                log.error("Erreur parsing JSON des pièces jointes pour page {}", pageId, e);
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
    }


    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private CompletableFuture<Optional<byte[]>> processDownloadFromResults(String pageId, String attachmentTitle, JsonNode results) {
        String downloadPath = findDownloadPath(results, attachmentTitle);
        if (isBlank(downloadPath)) {
            log.warn("Lien de téléchargement introuvable pour '{}' (page={})", attachmentTitle, pageId);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        URI downloadUri = buildDownloadUri(downloadPath);
        var downloadReq = buildDownloadRequest(downloadUri);
        return sendBytesWithRetry(downloadReq, config.maxRetries())
                .thenApply(bytesResp -> {
                    if (bytesResp.statusCode() == 200) {
                        return Optional.ofNullable(bytesResp.body());
                    }
                    log.warn("Téléchargement échoué (status={}) pour '{}' pageId={}", bytesResp.statusCode(), attachmentTitle, pageId);
                    return Optional.empty();
                });
    }

    private CompletableFuture<HttpResponse<byte[]>> sendBytesWithRetry(HttpRequest request, int retriesLeft) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    if (shouldRetry(response.statusCode()) && retriesLeft > 0) {
                        var delay = calculateBackoffDelay(config.maxRetries() - retriesLeft);
                        var delayedExecutor = CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                        return CompletableFuture.runAsync(() -> {}, delayedExecutor)
                                .thenCompose(_ -> sendBytesWithRetry(request, retriesLeft - 1));
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode >= 500 || statusCode == 429;
    }

    /**
     * Calcule le délai de backoff exponentiel
     */
    private long calculateBackoffDelay(int attempt) {
        return (long) (Math.pow(2, attempt) * 1000); // 1s, 2s, 4s, etc.
    }

    private String findDownloadPath(JsonNode results, String attachmentTitle) {
        if (results == null || isBlank(attachmentTitle)) {
            return null;
        }
        for (JsonNode node : results) {
            String title = extractTitle(node);
            if (attachmentTitle.equals(title)) {
                return node.path("_links").path("download").asText(null);
            }
        }
        return null;
    }

    private String extractTitle(JsonNode node) {
        return node != null && node.hasNonNull(TITLE_FIELD) ? node.get(TITLE_FIELD).asText() : null;
    }

    // Note: local URI construction kept here to avoid premature abstraction.
    // If this logic is duplicated elsewhere, consider extracting a ConfluenceUriBuilder later.
    private URI buildDownloadUri(String downloadPath) {
        String base = config.baseUrl();
        if (isBlank(base)) {
            // If base is blank, let URI handler interpret the path (absolute or relative)
            return URI.create(downloadPath);
        }
        return resolveAgainstConfluenceBase(base, downloadPath);
    }

    private static String buildNormalizedBaseUrl(final String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Resolves a Confluence download path against the configured base URL robustly.
     * Business rules:
     * - If downloadPath is absolute (http/https), return as-is.
     * - Confluence Cloud expects download paths under '/wiki/download/...'. If the path starts with '/download/',
     *   we prefix it with '/wiki' to avoid 404 on Cloud when base URL does not contain '/wiki'.
     * - Otherwise, resolve the path against the base URL using URI resolution.
     */
    private URI resolveAgainstConfluenceBase(String base, String downloadPath) {
        if (isBlank(downloadPath)) {
            return URI.create(buildNormalizedBaseUrl(base));
        }
        String dp = downloadPath.trim();
        if (dp.startsWith("http://") || dp.startsWith("https://")) {
            return URI.create(dp);
        }
        String normalizedBase = buildNormalizedBaseUrl(base) + "/";
        String path = dp;
        // Only normalize Confluence Cloud attachment download endpoints
        if (path.startsWith("/download/attachments/")) {
            path = "/wiki" + path;
        }
        URI baseUri = URI.create(normalizedBase);
        return baseUri.resolve(path);
    }

    private HttpRequest buildDownloadRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header(AUTHORIZATION_HEADER_NAME, authHeader)
                .timeout(Duration.ofMillis(config.readTimeout()))
                .GET()
                .build();
    }
}
