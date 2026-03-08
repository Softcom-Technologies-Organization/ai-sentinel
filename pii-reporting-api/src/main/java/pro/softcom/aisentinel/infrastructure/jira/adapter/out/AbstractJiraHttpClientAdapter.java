package pro.softcom.aisentinel.infrastructure.jira.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.jira.port.out.JiraClient;
import pro.softcom.aisentinel.domain.jira.JiraAttachmentInfo;
import pro.softcom.aisentinel.domain.jira.JiraComment;
import pro.softcom.aisentinel.domain.jira.JiraIssue;
import pro.softcom.aisentinel.domain.jira.JiraProject;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.http.HttpRetryExecutor;
import pro.softcom.aisentinel.infrastructure.jira.adapter.out.config.JiraConnectionConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Shared logic for Jira HTTP adapters (Cloud and Data Center).
 * <p>
 * Subclasses must implement the four template methods that differ between Cloud and DC:
 * authentication header, text-content parsing, date parsing, project retrieval, and
 * attachment-content download.
 */
@Slf4j
public abstract class AbstractJiraHttpClientAdapter implements JiraClient {

    protected static final String AUTHORIZATION_HEADER = "Authorization";
    protected static final String ACCEPT_HEADER = "Accept";
    protected static final String CONTENT_TYPE_JSON = "application/json";
    protected static final String CONTENT_TYPE_HEADER = "Content-Type";
    protected static final int HTTP_OK = 200;
    protected static final int DEFAULT_PAGE_SIZE = 50;

    protected static final DateTimeFormatter JQL_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("UTC"));

    protected static final Pattern PROJECT_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]+");
    protected static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    protected static final String ISSUE_FIELD_DESCRIPTION = "description";
    protected static final String ISSUE_FIELD_COMMENT = "comment";
    protected static final String ISSUE_FIELD_ATTACHMENT = "attachment";
    protected static final String ISSUE_FIELD_CREATED = "created";
    protected static final String ISSUE_FIELD_UPDATED = "updated";
    protected static final String FIELD_NAME_PROJECT = "project";
    protected static final String FIELD_NAME_TOTAL = "total";
    protected static final String DISPLAY_NAME = "displayName";
    protected static final String FIELD_NAME_FIELDS = "fields";
    protected static final String FIELD_NAME_COMMENTS = "comments";
    protected static final String FIELD_NAME_AUTHOR = "author";

    protected static final List<String> ISSUE_FIELDS = List.of(
        "summary", ISSUE_FIELD_DESCRIPTION, ISSUE_FIELD_COMMENT, ISSUE_FIELD_ATTACHMENT, FIELD_NAME_PROJECT,
        ISSUE_FIELD_CREATED, ISSUE_FIELD_UPDATED, "reporter", "assignee", "status", "issuetype"
    );
    public static final String FIELD_NAME_INSIGHT = "insight";

    protected final JiraConnectionConfig config;
    protected final ObjectMapper objectMapper;
    protected final HttpRetryExecutor retryExecutor;

    protected AbstractJiraHttpClientAdapter(JiraConnectionConfig config,
                                            ObjectMapper objectMapper,
                                            HttpRetryExecutor retryExecutor) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.retryExecutor = retryExecutor;
    }

    // --- Template methods (implemented by subclasses) ---

    /** Returns the value of the HTTP Authorization header (e.g. "Basic ..." or "Bearer ..."). */
    protected abstract String getAuthHeader();

    /** Parses a text-content JSON field (ADF for Cloud, raw string for Data Center). */
    protected abstract String parseTextContent(JsonNode node);

    /** Parses an Instant from a JSON fields node (different date formats between Cloud and DC). */
    protected abstract Instant parseDate(JsonNode fieldsOrNode, String fieldName);

    // --- JiraClient implementation (common) ---

    @Override
    public CompletableFuture<Boolean> testConnection() {
        log.info("Testing connection to Jira");
        var uri = URI.create(config.getRestApiUrl() + config.myselfPath());
        var request = buildGetRequest(uri);
        return retryExecutor.executeRequest(request)
            .thenApply(response -> response.statusCode() == HTTP_OK)
            .exceptionally(ex -> {
                log.error("Jira connection test failed", ex);
                return false;
            });
    }

    @Override
    public CompletableFuture<List<JiraIssue>> getIssuesInProject(String projectKey) {
        validateProjectKey(projectKey);
        log.info("Retrieving issues for project: {}", projectKey);
        var jql = "project = " + projectKey + " ORDER BY updated DESC";
        return collectAllIssuesRecursively(jql, 0, new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<JiraIssue>> getIssuesUpdatedSince(String projectKey, Instant since) {
        validateProjectKey(projectKey);
        log.info("Retrieving issues updated since {} for project: {}", since, projectKey);
        var sinceFormatted = JQL_DATE_FORMATTER.format(since);
        var jql = "project = " + projectKey + " AND updated >= '" + sinceFormatted + "' ORDER BY updated DESC";
        return collectAllIssuesRecursively(jql, 0, new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<JiraComment>> getAllComments(String issueKey) {
        validateIssueKey(issueKey);
        log.info("Retrieving all comments for issue: {}", issueKey);
        return collectAllCommentsRecursively(issueKey, 0, new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<JiraAttachmentInfo>> getAttachments(String issueKey) {
        validateIssueKey(issueKey);
        log.info("Retrieving attachments for issue: {}", issueKey);
        var jql = "key = " + issueKey;
        var body = buildSearchBody(jql, 0, 1, List.of(ISSUE_FIELD_ATTACHMENT));
        var uri = URI.create(config.getRestApiUrl() + config.searchPath());
        var request = buildPostRequest(uri, body);
        return retryExecutor.executeRequest(request)
            .thenApply(this::parseAttachmentsFromSearchResponse);
    }

    // --- Validation ---

    protected static void validateProjectKey(String projectKey) {
        if (projectKey == null || !PROJECT_KEY_PATTERN.matcher(projectKey).matches()) {
            throw new IllegalArgumentException("Invalid Jira project key: " + projectKey);
        }
    }

    protected static void validateIssueKey(String issueKey) {
        if (issueKey == null || !ISSUE_KEY_PATTERN.matcher(issueKey).matches()) {
            throw new IllegalArgumentException("Invalid Jira issue key: " + issueKey);
        }
    }

    // --- Issue search with JQL (POST /search) ---

    protected CompletableFuture<List<JiraIssue>> collectAllIssuesRecursively(
        String jql, int startAt, List<JiraIssue> accumulated
    ) {
        if (accumulated.size() >= config.maxIssues()) {
            log.warn("Max issues limit ({}) reached", config.maxIssues());
            return CompletableFuture.completedFuture(accumulated);
        }

        var body = buildSearchBody(jql, startAt, config.issuesLimit(), ISSUE_FIELDS);
        var uri = URI.create(config.getRestApiUrl() + config.searchPath());
        var request = buildPostRequest(uri, body);

        return retryExecutor.executeRequest(request)
            .thenCompose(response -> processIssuesBatch(response, jql, startAt, accumulated));
    }

    protected CompletableFuture<List<JiraIssue>> processIssuesBatch(
        HttpResponse<String> response, String jql, int startAt, List<JiraIssue> accumulated
    ) {
        if (response.statusCode() != HTTP_OK) {
            log.error("Error searching issues: HTTP {}", response.statusCode());
            return CompletableFuture.completedFuture(accumulated);
        }

        try {
            var root = objectMapper.readTree(response.body());
            var issues = root.get("issues");
            if (issues == null || !issues.isArray() || issues.isEmpty()) {
                return CompletableFuture.completedFuture(accumulated);
            }

            var batch = parseIssues(issues);
            var merged = new ArrayList<>(accumulated);
            merged.addAll(batch);

            var total = root.has(FIELD_NAME_TOTAL) ? root.get(FIELD_NAME_TOTAL).asInt() : 0;
            if (merged.size() < total && merged.size() < config.maxIssues()) {
                return collectAllIssuesRecursively(jql, startAt + issues.size(), merged);
            }
            return CompletableFuture.completedFuture(merged);
        } catch (Exception e) {
            log.error("Error parsing issues response", e);
            return CompletableFuture.completedFuture(accumulated);
        }
    }

    protected List<JiraIssue> parseIssues(JsonNode issuesNode) {
        var issues = new ArrayList<JiraIssue>();
        for (var node : issuesNode) {
            issues.add(parseSingleIssue(node));
        }
        return issues;
    }

    private JiraIssue parseSingleIssue(JsonNode node) {
        var id = textOrEmpty(node, "id");
        var key = textOrEmpty(node, "key");
        var fields = node.get(FIELD_NAME_FIELDS);
        if (fields == null) {
            return JiraIssue.builder().id(id).key(key).build();
        }

        var summary = textOrEmpty(fields, "summary");
        var projectKey = extractProjectKey(fields);
        var descriptionText = parseTextContent(fields.get(ISSUE_FIELD_DESCRIPTION));
        var comments = parseEmbeddedComments(fields);
        var metadata = parseIssueMetadata(fields);

        return JiraIssue.builder()
            .id(id)
            .key(key)
            .projectKey(projectKey)
            .summary(summary)
            .descriptionText(descriptionText)
            .comments(comments)
            .metadata(metadata)
            .build();
    }

    protected String extractProjectKey(JsonNode fields) {
        if (fields.has(FIELD_NAME_PROJECT) && fields.get(FIELD_NAME_PROJECT).has("key")) {
            return fields.get(FIELD_NAME_PROJECT).get("key").asText("");
        }
        return "";
    }

    protected List<JiraComment> parseEmbeddedComments(JsonNode fields) {
        var comments = new ArrayList<JiraComment>();
        if (!fields.has(ISSUE_FIELD_COMMENT)) {
            return comments;
        }
        var commentField = fields.get(ISSUE_FIELD_COMMENT);
        var commentsArray = commentField.has(FIELD_NAME_COMMENTS) ? commentField.get(FIELD_NAME_COMMENTS) : null;
        if (commentsArray == null || !commentsArray.isArray()) {
            return comments;
        }
        for (var cNode : commentsArray) {
            comments.add(parseSingleComment(cNode));
        }
        return comments;
    }

    protected JiraIssue.IssueMetadata parseIssueMetadata(JsonNode fields) {
        var reporter = extractDisplayName(fields, "reporter");
        var assignee = extractDisplayName(fields, "assignee");
        var status = extractNameField(fields, "status");
        var issueType = extractNameField(fields, "issuetype");
        var created = parseDate(fields, ISSUE_FIELD_CREATED);
        var updated = parseDate(fields, ISSUE_FIELD_UPDATED);
        return new JiraIssue.IssueMetadata(reporter, assignee, status, issueType, created, updated);
    }

    protected String extractDisplayName(JsonNode fields, String fieldName) {
        if (fields.has(fieldName) && !fields.get(fieldName).isNull()
                && fields.get(fieldName).has(DISPLAY_NAME)) {
            return fields.get(fieldName).get(DISPLAY_NAME).asText("");
        }
        return null;
    }

    protected String extractNameField(JsonNode fields, String fieldName) {
        if (fields.has(fieldName) && !fields.get(fieldName).isNull()
                && fields.get(fieldName).has("name")) {
            return fields.get(fieldName).get("name").asText("");
        }
        return null;
    }

    // --- Comments pagination ---

    protected CompletableFuture<List<JiraComment>> collectAllCommentsRecursively(
        String issueKey, int startAt, List<JiraComment> accumulated
    ) {
        var uri = URI.create(config.getRestApiUrl()
            + config.issuePath() + issueKey + config.commentPath()
            + "?startAt=" + startAt + "&maxResults=" + DEFAULT_PAGE_SIZE);
        var request = buildGetRequest(uri);

        return retryExecutor.executeRequest(request)
            .thenCompose(response -> processCommentsBatch(response, issueKey, startAt, accumulated));
    }

    protected CompletableFuture<List<JiraComment>> processCommentsBatch(
        HttpResponse<String> response, String issueKey, int startAt, List<JiraComment> accumulated
    ) {
        if (response.statusCode() != HTTP_OK) {
            log.error("Error retrieving comments for {}: HTTP {}", issueKey, response.statusCode());
            return CompletableFuture.completedFuture(accumulated);
        }

        try {
            var root = objectMapper.readTree(response.body());
            var comments = root.get(FIELD_NAME_COMMENTS);
            if (comments == null || !comments.isArray() || comments.isEmpty()) {
                return CompletableFuture.completedFuture(accumulated);
            }

            var batch = new ArrayList<JiraComment>();
            for (var cNode : comments) {
                batch.add(parseSingleComment(cNode));
            }

            var merged = new ArrayList<>(accumulated);
            merged.addAll(batch);

            var total = root.has(FIELD_NAME_TOTAL) ? root.get(FIELD_NAME_TOTAL).asInt() : 0;
            if (merged.size() < total) {
                return collectAllCommentsRecursively(issueKey, startAt + comments.size(), merged);
            }
            return CompletableFuture.completedFuture(merged);
        } catch (Exception e) {
            log.error("Error parsing comments for {}", issueKey, e);
            return CompletableFuture.completedFuture(accumulated);
        }
    }

    protected JiraComment parseSingleComment(JsonNode node) {
        var id = textOrEmpty(node, "id");
        var authorName = "";
        if (node.has(FIELD_NAME_AUTHOR) && node.get(FIELD_NAME_AUTHOR).has(DISPLAY_NAME)) {
            authorName = node.get(FIELD_NAME_AUTHOR).get(DISPLAY_NAME).asText("");
        }
        var bodyText = parseTextContent(node.get("body"));
        var created = parseDate(node, ISSUE_FIELD_CREATED);
        var updated = parseDate(node, ISSUE_FIELD_UPDATED);
        return new JiraComment(id, authorName, bodyText, created, updated);
    }

    // --- Attachment parsing from search response ---

    protected List<JiraAttachmentInfo> parseAttachmentsFromSearchResponse(HttpResponse<String> response) {
        if (response.statusCode() != HTTP_OK) {
            return List.of();
        }
        try {
            var root = objectMapper.readTree(response.body());
            var issues = root.get("issues");
            if (issues == null || !issues.isArray() || issues.isEmpty()) {
                return List.of();
            }
            var fields = issues.get(0).get(FIELD_NAME_FIELDS);
            if (fields == null || !fields.has(ISSUE_FIELD_ATTACHMENT)) {
                return List.of();
            }
            var attachments = fields.get(ISSUE_FIELD_ATTACHMENT);
            if (!attachments.isArray()) {
                return List.of();
            }
            var result = new ArrayList<JiraAttachmentInfo>();
            for (var aNode : attachments) {
                result.add(parseSingleAttachment(aNode));
            }
            return result;
        } catch (Exception e) {
            log.error("Error parsing attachments response", e);
            return List.of();
        }
    }

    protected JiraAttachmentInfo parseSingleAttachment(JsonNode node) {
        var id = textOrEmpty(node, "id");
        var filename = textOrEmpty(node, "filename");
        var mimeType = textOrEmpty(node, "mimeType");
        var size = node.has("size") ? node.get("size").asLong(0) : 0;
        var contentUrl = textOrEmpty(node, "content");
        var author = "";
        if (node.has(FIELD_NAME_AUTHOR) && node.get(FIELD_NAME_AUTHOR).has(DISPLAY_NAME)) {
            author = node.get(FIELD_NAME_AUTHOR).get(DISPLAY_NAME).asText("");
        }
        return new JiraAttachmentInfo(id, filename, mimeType, size, contentUrl, author);
    }

    // --- Project parsing (shared helper for parseSingleProject) ---

    protected JiraProject parseSingleProject(JsonNode node, boolean hasInsightCount) {
        var id = textOrEmpty(node, "id");
        var key = textOrEmpty(node, "key");
        var name = textOrEmpty(node, "name");
        var description = textOrEmpty(node, ISSUE_FIELD_DESCRIPTION);

        var leadName = "";
        if (node.has("lead") && node.get("lead").has(DISPLAY_NAME)) {
            leadName = node.get("lead").get(DISPLAY_NAME).asText("");
        }

        var rawBaseUrl = config.baseUrl();
        var baseUrl = (rawBaseUrl != null && !rawBaseUrl.isBlank()) ? rawBaseUrl.replaceAll("/+$", "") : "";
        var url = baseUrl.isEmpty() ? "" : baseUrl + "/projects/" + key;

        var issueCount = 0;
        if (hasInsightCount && node.has(FIELD_NAME_INSIGHT) && node.get(FIELD_NAME_INSIGHT).has("totalIssueCount")) {
            issueCount = node.get(FIELD_NAME_INSIGHT).get("totalIssueCount").asInt(0);
        }

        return new JiraProject(id, key, name, description, leadName, url, issueCount, null);
    }

    // --- HTTP request builders ---

    protected HttpRequest buildGetRequest(URI uri) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER, getAuthHeader())
            .header(ACCEPT_HEADER, CONTENT_TYPE_JSON)
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();
    }

    protected HttpRequest buildGetRequestForBytes(URI uri) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER, getAuthHeader())
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();
    }

    protected HttpRequest buildPostRequest(URI uri, String body) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER, getAuthHeader())
            .header(ACCEPT_HEADER, CONTENT_TYPE_JSON)
            .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
            .timeout(Duration.ofMillis(config.readTimeout()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    }

    protected String buildSearchBody(String jql, int startAt, int maxResults, List<String> fields) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("jql", jql);
            root.put("startAt", startAt);
            root.put("maxResults", maxResults);
            var fieldsArray = root.putArray(FIELD_NAME_FIELDS);
            fields.forEach(fieldsArray::add);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Error building search body", e);
            throw new IllegalStateException("Failed to build search request body", e);
        }
    }

    protected String textOrEmpty(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText("") : "";
    }
}
