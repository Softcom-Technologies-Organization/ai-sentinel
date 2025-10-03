package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceAttachmentClient;
import pro.softcom.sentinelle.domain.confluence.AttachmentInfo;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;


@Service
public class ConfluenceAttachmentHttpClientAdapter implements ConfluenceAttachmentClient {

    private static final Logger logger = LoggerFactory.getLogger(
        ConfluenceAttachmentHttpClientAdapter.class);
    // Paths moved to configuration to avoid hard-coded URIs (Sonar)
    public static final String ACCEPT_HEADER_NAME = "Accept";
    public static final String CONTENT_TYPE_HEADER_VALUE = "application/json";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    // Sonar S1192: avoid duplicate literals
    private static final String RESULTS_FIELD = "results";
    private static final String TITLE_FIELD = "title";

    private final ConfluenceConnectionConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authHeader;

    public ConfluenceAttachmentHttpClientAdapter(@Qualifier("confluenceConfig") ConfluenceConnectionConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        // Utilisation des Virtual Threads pour une meilleure scalabilité
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        this.httpClient = HttpClient.newBuilder().executor(executor)
            .connectTimeout(Duration.ofMillis(config.connectTimeout()))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        // Préparation de l'authentification Basic
        this.authHeader = createAuthHeader(config.username(), config.apiToken());
    }


    public CompletableFuture<List<AttachmentInfo>> getPageAttachments(String pageId) {
        logger.info("Récupération des pièces jointes de la page: {}", pageId);

        var uri = URI.create(config.getRestApiUrl() + config.contentPath() + pageId + config.attachmentChildSuffix() + "?limit=200&expand=results._links,results.metadata");

        var request = HttpRequest.newBuilder().uri(uri)
            .header(AUTHORIZATION_HEADER_NAME, authHeader)
            .header(ACCEPT_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE)
            .timeout(Duration.ofMillis(config.readTimeout())).GET().build();

        return sendRequestAsync(request).thenApply(response -> {
            if (response.statusCode() == 200) {
                try {
                    long t1 = System.currentTimeMillis();
                    JsonNode root = objectMapper.readTree(response.body());
                    logger.info("jsonNode read in {} ms", System.currentTimeMillis() - t1);
                    JsonNode results = root.get(RESULTS_FIELD);
                    if (results == null || !results.isArray()) {
                        return List.of();
                    }
                    return mapResultsToAttachments(results);
                } catch (Exception e) {
                    logger.error("Erreur lors de la désérialisation des pièces jointes", e);
                    return List.of();
                }
            }
            if (response.statusCode() == 404) {
                logger.warn("Aucune pièce jointe ou page non trouvée: {}", pageId);
                return List.of();
            }
            logger.error("Erreur lors de la récupération des pièces jointes: {}", response.statusCode());
            return List.of();
        });
    }


    private CompletableFuture<HttpResponse<String>> sendRequestAsync(HttpRequest request) {
        return sendRequestWithRetry(request, config.maxRetries());
    }

    private CompletableFuture<HttpResponse<String>> sendRequestWithRetry(HttpRequest request, int retriesLeft) {

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (shouldRetry(response.statusCode()) && retriesLeft > 0) {
                var delay = calculateBackoffDelay(config.maxRetries() - retriesLeft);
                logger.warn("Retry de la requête après {} ms, tentatives restantes: {}", delay, retriesLeft);

                // Créer un délai avant de réessayer avec CompletableFuture.delayedExecutor
                var delayedExecutor = CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS);

                return CompletableFuture.runAsync(() -> {
                }, delayedExecutor).thenCompose(_ -> sendRequestWithRetry(request, retriesLeft - 1));
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode >= 500 || statusCode == 429; // Erreurs serveur ou rate limiting
    }

    private long calculateBackoffDelay(int attempt) {
        return (long) (Math.pow(2, attempt) * 1000); // 1s, 2s, 4s, etc.
    }

    private String createAuthHeader(String username, String apiToken) {
        var credentials = username + ":" + apiToken;
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }

    /**
     * Maps Confluence JSON 'results' to domain AttachmentInfo list.
     * What: extract title, extension, mediaType and absolute download URL.
     * Why: centralize parsing and reduce cognitive complexity of callers.
     */
    private List<AttachmentInfo> mapResultsToAttachments(JsonNode results) {
        List<AttachmentInfo> attachments = new ArrayList<>();
        for (JsonNode node : results) {
            String title = extractTitle(node);
            if (isBlank(title)) {
                continue;
            }
            String ext = AttachmentInfo.extractExtension(title);
            String downloadPath = node.path("_links").path("download").asText(null);
            String mediaType = node.path("metadata").path("mediaType").asText(null);
            String absoluteUrl = computeAbsoluteUrl(downloadPath);
            attachments.add(new AttachmentInfo(title, ext, mediaType, absoluteUrl));
        }
        return attachments;
    }

    /**
     * Returns the node title when present, null otherwise.
     */
    private String extractTitle(JsonNode node) {
        return node != null && node.hasNonNull(TITLE_FIELD) ? node.get(TITLE_FIELD).asText() : null;
    }

    /**
     * Computes an absolute URL from a Confluence download path and base URL.
     * Returns null if input path or base URL are missing.
     */
    private String computeAbsoluteUrl(String downloadPath) {
        if (isBlank(downloadPath)) {
            return null;
        }
        String base = config.baseUrl();
        if (isBlank(base)) {
            return null;
        }
        return resolveAgainstConfluenceBase(base, downloadPath).toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }


    private static String buildNormalizedBaseUrl(final String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Resolves a Confluence download path against the configured base URL robustly.
     * Business rules:
     * - If downloadPath is absolute (http/https), return as-is.
     * - Confluence Cloud expects download paths under '/wiki/download/...'. If the path starts with '/download/',
     * we prefix it with '/wiki' to avoid 404 on Cloud when base URL does not contain '/wiki'.
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
}
