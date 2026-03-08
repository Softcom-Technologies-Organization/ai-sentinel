package pro.softcom.aisentinel.infrastructure.jira.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.domain.jira.JiraComment;
import pro.softcom.aisentinel.domain.jira.JiraProject;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.http.HttpRetryExecutor;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.config.JiraConnectionConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP Adapter for Jira Server/Data Center REST API v2.
 * <p>
 * Key differences from Jira Cloud (v3):
 * <ul>
 *   <li>Uses {@code /rest/api/2} endpoints</li>
 *   <li>Descriptions and comments are wiki markup strings (not ADF JSON)</li>
 *   <li>{@code /rest/api/2/project} returns all projects as a flat JSON array (no pagination)</li>
 *   <li>Authentication: Bearer token (Personal Access Token, Jira DC 8.14+)</li>
 *   <li>Attachment content is fetched via the {@code content} URL from attachment metadata</li>
 *   <li>Date format: {@code yyyy-MM-dd'T'HH:mm:ss.SSSZ} (offset without colon)</li>
 * </ul>
 */
@Slf4j
public class JiraDataCenterHttpClientAdapter extends AbstractJiraHttpClientAdapter implements AutoCloseable {

    private static final String BEARER_AUTH_PREFIX = "Bearer ";
    private static final DateTimeFormatter JIRA_DC_DATE_PARSER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final int MAX_COMMENTS_PER_ISSUE = 500;

    private final ExecutorService httpExecutor;

    public JiraDataCenterHttpClientAdapter(JiraConnectionConfig config, ObjectMapper objectMapper) {
        this(config, objectMapper, Executors.newVirtualThreadPerTaskExecutor());
    }

    private JiraDataCenterHttpClientAdapter(JiraConnectionConfig config, ObjectMapper objectMapper,
                                            ExecutorService sharedExecutor) {
        super(config, objectMapper, buildRetryExecutor(config, sharedExecutor));
        this.httpExecutor = sharedExecutor;
    }

    JiraDataCenterHttpClientAdapter(JiraConnectionConfig config, ObjectMapper objectMapper,
                                    HttpRetryExecutor retryExecutor) {
        super(config, objectMapper, retryExecutor);
        this.httpExecutor = null;
    }

    private static HttpRetryExecutor buildRetryExecutor(JiraConnectionConfig config, ExecutorService executor) {
        var client = buildHttpClient(config, executor);
        return new HttpRetryExecutor(client, config.maxRetries());
    }

    @Override
    public void close() {
        if (httpExecutor != null) {
            httpExecutor.close();
        }
    }

    // --- Template method implementations ---

    @Override
    protected String getAuthHeader() {
        return BEARER_AUTH_PREFIX + config.apiToken().trim();
    }

    @Override
    protected String parseTextContent(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    @Override
    protected Instant parseDate(JsonNode fieldsOrNode, String fieldName) {
        if (!fieldsOrNode.has(fieldName) || fieldsOrNode.get(fieldName).isNull()) {
            return null;
        }
        return parseJiraDateString(fieldsOrNode.get(fieldName).asText());
    }

    private Instant parseJiraDateString(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateString, JIRA_DC_DATE_PARSER).toInstant();
        } catch (Exception _) {
            try {
                return Instant.parse(dateString);
            } catch (Exception _) {
                log.debug("Unable to parse Jira date: {}", dateString);
                return null;
            }
        }
    }

    // --- DC-specific: flat project array (no pagination) ---

    @Override
    public CompletableFuture<List<JiraProject>> getAllProjects() {
        log.info("Retrieving all Jira DC projects");
        var uri = URI.create(config.getRestApiUrl() + config.projectPath());
        var request = buildGetRequest(uri);
        return retryExecutor.executeRequest(request)
            .thenApply(this::parseProjectsResponse);
    }

    private List<JiraProject> parseProjectsResponse(HttpResponse<String> response) {
        if (response.statusCode() != HTTP_OK) {
            log.error("Error retrieving projects: HTTP {}", response.statusCode());
            return List.of();
        }
        try {
            var root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return List.of();
            }
            var projects = new ArrayList<JiraProject>();
            for (var node : root) {
                projects.add(parseSingleProject(node, false));
            }
            return projects;
        } catch (Exception e) {
            log.error("Error parsing projects response", e);
            return List.of();
        }
    }

    // --- DC-specific: max comments limit ---

    @Override
    protected CompletableFuture<List<JiraComment>> collectAllCommentsRecursively(
        String issueKey, int startAt, List<JiraComment> accumulated
    ) {
        if (accumulated.size() >= MAX_COMMENTS_PER_ISSUE) {
            log.warn("Max comments limit ({}) reached for issue {}", MAX_COMMENTS_PER_ISSUE, issueKey);
            return CompletableFuture.completedFuture(accumulated);
        }
        return super.collectAllCommentsRecursively(issueKey, startAt, accumulated);
    }

    // --- DC-specific: getAttachments validates issue key ---

    @Override
    public CompletableFuture<List<pro.softcom.aisentinel.domain.jira.JiraAttachmentInfo>> getAttachments(String issueKey) {
        validateIssueKey(issueKey);
        return super.getAttachments(issueKey);
    }

    // --- DC-specific: attachment download via metadata + host validation ---

    @Override
    public CompletableFuture<byte[]> getAttachmentContent(String attachmentId) {
        log.info("Downloading attachment content: {}", attachmentId);
        var metadataUri = URI.create(config.getRestApiUrl() + config.attachmentPath() + attachmentId);
        var metadataRequest = buildGetRequest(metadataUri);
        return retryExecutor.executeRequest(metadataRequest)
            .thenCompose(this::downloadAttachmentFromMetadata);
    }

    private CompletableFuture<byte[]> downloadAttachmentFromMetadata(HttpResponse<String> metadataResponse) {
        if (metadataResponse.statusCode() != HTTP_OK) {
            log.error("Error fetching attachment metadata: HTTP {}", metadataResponse.statusCode());
            return CompletableFuture.completedFuture(new byte[0]);
        }
        try {
            var metadata = objectMapper.readTree(metadataResponse.body());
            var contentUrl = textOrEmpty(metadata, "content");
            if (contentUrl.isEmpty()) {
                return CompletableFuture.completedFuture(new byte[0]);
            }
            if (!isHostValid(contentUrl)) {
                return CompletableFuture.completedFuture(new byte[0]);
            }
            var downloadRequest = buildGetRequestForBytes(URI.create(contentUrl));
            return retryExecutor.executeRequest(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != HTTP_OK) {
                        log.error("Error downloading attachment: HTTP {}", response.statusCode());
                        return new byte[0];
                    }
                    return response.body();
                });
        } catch (Exception e) {
            log.error("Error parsing attachment metadata", e);
            return CompletableFuture.completedFuture(new byte[0]);
        }
    }

    private boolean isHostValid(String contentUrl) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            log.error("Cannot validate attachment URL: base URL not configured");
            return false;
        }
        var expectedUri = URI.create(config.baseUrl());
        var expectedHost = expectedUri.getHost();
        if (expectedHost == null) {
            log.error("Cannot validate attachment URL: unable to parse host from base URL");
            return false;
        }
        var contentUri = URI.create(contentUrl);
        if (!contentUri.getScheme().equalsIgnoreCase(expectedUri.getScheme())) {
            log.error("Attachment content URL scheme mismatch: expected {}, got {}",
                expectedUri.getScheme(), contentUri.getScheme());
            return false;
        }
        if (!contentUri.getHost().equalsIgnoreCase(expectedHost)) {
            log.error("Attachment content URL host mismatch: expected {}, got {}",
                expectedHost, contentUri.getHost());
            return false;
        }
        if (contentUri.getPort() != expectedUri.getPort()) {
            log.error("Attachment content URL port mismatch: expected {}, got {}",
                expectedUri.getPort(), contentUri.getPort());
            return false;
        }
        return true;
    }

    // --- HTTP client factory ---

    private static HttpClient buildHttpClient(JiraConnectionConfig config, ExecutorService executor) {
        return HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofMillis(config.connectTimeout()))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}
