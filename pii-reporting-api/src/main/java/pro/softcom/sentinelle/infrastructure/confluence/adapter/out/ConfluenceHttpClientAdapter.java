package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceClient;
import pro.softcom.sentinelle.domain.confluence.ConfluencePage;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.domain.confluence.ModifiedAttachmentInfo;
import pro.softcom.sentinelle.domain.confluence.ModifiedPageInfo;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluencePageDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpaceDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.dto.ConfluenceSpacesResponseDto;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.http.ConfluenceApiUrlBuilder;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.http.HttpRetryExecutor;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.mapper.ConfluencePageMapper;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.pagination.ConfluencePaginationHandler;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.parser.ConfluenceResponseParser;


/**
 * HTTP Adapter for Confluence REST API.
 * Handles read and update operations for Confluence pages and spaces.
 */
@Service
@Slf4j
public class ConfluenceHttpClientAdapter implements ConfluenceClient {

    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String CONTENT_TYPE_HEADER_VALUE = "application/json";
    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String RESULTS_FIELD = "results";
    private static final DateTimeFormatter CQL_DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("UTC"));
    public static final String TYPE_FIELD_NAME = "type";
    public static final String TITLE_FIELD_NAME = "title";
    public static final String ID_FIELD_NAME = "id";
    public static final String PAGE_FIELD_NAME = "page";

    private final ConfluenceConnectionConfig config;
    private final ObjectMapper objectMapper;
    private final String authHeader;
    private final ConfluenceApiUrlBuilder urlBuilder;
    private final HttpRetryExecutor retryExecutor;
    private final ConfluencePaginationHandler paginationHandler;
    private final ConfluenceResponseParser responseParser;

    public ConfluenceHttpClientAdapter(
        @Qualifier("confluenceConfig") ConfluenceConnectionConfig config,
        ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.authHeader = encodeBasicAuth(config.username(), config.apiToken());
        this.urlBuilder = new ConfluenceApiUrlBuilder(config);
        this.retryExecutor = new HttpRetryExecutor(buildHttpClient(), config.maxRetries());
        this.paginationHandler = new ConfluencePaginationHandler();
        this.responseParser = new ConfluenceResponseParser(objectMapper);
    }

    @Override
    public CompletableFuture<Optional<ConfluencePage>> getPage(String pageId) {
        log.info("Retrieving Confluence page: {}", pageId);
        var request = buildPageRequest(pageId);
        return retryExecutor.executeRequest(request).thenApply(response -> parsePageResponse(response, pageId));
    }

    @Override
    public CompletableFuture<List<ConfluencePage>> searchPages(String spaceKey, String query) {
        log.info("Searching pages in space {} with query: {}", spaceKey, query);
        
        if (isBlankQuery(query)) {
            return retrieveAllPagesFromSpace(spaceKey);
        }
        
        return searchPagesWithQuery(spaceKey, query);
    }

    @Override
    public CompletableFuture<List<ConfluencePage>> getAllPagesInSpace(String spaceKey) {
        log.info("Retrieving all pages in space: {}", spaceKey);
        return retrieveAllPagesFromSpace(spaceKey);
    }

    @Override
    public CompletableFuture<ConfluencePage> updatePage(ConfluencePage page) {
        log.info("Updating page: {}", page.id());
        var pageDto = ConfluencePageMapper.fromDomain(page);
        
        try {
            var requestBody = serializePageDto(pageDto);
            var request = buildUpdatePageRequest(page.id(), requestBody);
            return retryExecutor.executeRequest(request).thenApply(this::parseUpdatedPage);
        } catch (Exception e) {
            log.error("Error serializing page", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Optional<ConfluenceSpace>> getSpace(String spaceKey) {
        log.info("Retrieving space: {}", spaceKey);
        var request = buildSpaceRequest(spaceKey);
        return retryExecutor.executeRequest(request).thenApply(this::parseSpaceResponse);
    }

    @Override
    public CompletableFuture<Optional<ConfluenceSpace>> getSpaceWithPermissions(String spaceKey) {
        log.info("Retrieving space with permissions: {}", spaceKey);
        var request = buildSpaceRequestWithPermissions(spaceKey);
        return retryExecutor.executeRequest(request)
                .thenApply(this::parseSpaceResponse);
    }

    @Override
    public CompletableFuture<Optional<ConfluenceSpace>> getSpaceById(String spaceId) {
        log.info("Retrieving space by ID: {}", spaceId);
        var request = buildSpaceRequest(spaceId);
        return retryExecutor.executeRequest(request).thenApply(this::parseSpaceResponse);
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        log.info("Testing connection to Confluence");
        var request = buildConnectionTestRequest();
        return retryExecutor.executeRequest(request)
            .thenApply(this::isConnectionSuccessful)
            .exceptionally(this::handleConnectionError);
    }

    @Override
    public CompletableFuture<List<ConfluenceSpace>> getAllSpaces() {
        log.info("Retrieving all Confluence spaces (pagination)");
        return retrieveAllSpaces();
    }

    private HttpClient buildHttpClient() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        return HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofMillis(config.connectTimeout()))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private String encodeBasicAuth(String username, String apiToken) {
        var credentials = username + ":" + apiToken;
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }

    private HttpRequest buildPageRequest(String pageId) {
        var uri = urlBuilder.buildPageUri(pageId);
        return buildGetRequest(uri);
    }

    private HttpRequest buildGetRequest(URI uri) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER_NAME, authHeader)
            .header(ACCEPT_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE)
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();
    }

    private Optional<ConfluencePage> parsePageResponse(HttpResponse<String> response, String pageId) {
        return switch (response.statusCode()) {
            case 200 -> responseParser.deserializePageDto(response.body());
            case 404 -> handlePageNotFound(pageId);
            default -> handlePageRetrievalError(response.statusCode());
        };
    }

    private Optional<ConfluencePage> handlePageNotFound(String pageId) {
        log.warn("Page not found: {}", pageId);
        return Optional.empty();
    }

    private Optional<ConfluencePage> handlePageRetrievalError(int statusCode) {
        log.error("Error retrieving page: {}", statusCode);
        return Optional.empty();
    }

    private boolean isBlankQuery(String query) {
        return query == null || query.isBlank();
    }

    private CompletableFuture<List<ConfluencePage>> searchPagesWithQuery(String spaceKey, String query) {
        var request = buildSearchRequest(spaceKey, query);
        return retryExecutor.executeRequest(request).thenApply(this::parseSearchResults);
    }

    private HttpRequest buildSearchRequest(String spaceKey, String query) {
        var cql = String.format("space='%s' AND text ~ '%s'", spaceKey, query);
        var uri = urlBuilder.buildSearchUri(cql);
        return buildGetRequest(uri);
    }

    private List<ConfluencePage> parseSearchResults(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            log.error("Error during search: {}", response.statusCode());
            return List.of();
        }
        return responseParser.deserializeSearchResults(response.body());
    }

    private CompletableFuture<List<ConfluencePage>> retrieveAllPagesFromSpace(String spaceKey) {
        return collectAllPagesRecursively(spaceKey, 0, config.pagesLimit(), config.maxPages());
    }

    private CompletableFuture<List<ConfluencePage>> collectAllPagesRecursively(
        String spaceKey, int startIndex, int pageSize, int remainingPagesLimit) {
        
        if (hasReachedPageLimit(remainingPagesLimit)) {
            log.warn("Page limit reached for space: {}", spaceKey);
            return CompletableFuture.completedFuture(List.of());
        }

        var request = buildSpacePagesRequest(spaceKey, startIndex, pageSize);
        return retryExecutor.executeRequest(request)
            .thenCompose(response -> processPagesBatch(response, spaceKey, startIndex, pageSize, remainingPagesLimit));
    }

    private boolean hasReachedPageLimit(int remainingPagesLimit) {
        return remainingPagesLimit <= 0;
    }

    private HttpRequest buildSpacePagesRequest(String spaceKey, int startIndex, int pageSize) {
        var uri = urlBuilder.buildSpacePagesUri(spaceKey, startIndex, pageSize);
        return buildGetRequest(uri);
    }

    private CompletableFuture<List<ConfluencePage>> processPagesBatch(
        HttpResponse<String> response, String spaceKey, int startIndex, int pageSize, int remainingPagesLimit) {
        
        if (response.statusCode() != 200) {
            return CompletableFuture.completedFuture(List.of());
        }

        try {
            var currentBatch = extractPagesFromResponse(response.body(), spaceKey);
            
            if (shouldFetchNextBatch(currentBatch, pageSize)) {
                return fetchNextBatchAndMerge(currentBatch, spaceKey, startIndex, pageSize, remainingPagesLimit);
            }
            
            return CompletableFuture.completedFuture(currentBatch);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des pages", e);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private List<ConfluencePage> extractPagesFromResponse(String responseBody, String spaceKey) throws IOException {
        var jsonNode = objectMapper.readTree(responseBody);
        var pageNode = jsonNode.get(PAGE_FIELD_NAME);

        if (pageNode == null || !pageNode.has(RESULTS_FIELD)) {
            return List.of();
        }

        var results = pageNode.get(RESULTS_FIELD);
        var pageDtos = deserializePageDtos(results);
        return enrichPagesWithSpaceKey(pageDtos, spaceKey);
    }

    private List<ConfluencePageDto> deserializePageDtos(com.fasterxml.jackson.databind.JsonNode results) {
        CollectionType pageDtoCollectionType = objectMapper.getTypeFactory()
            .constructCollectionType(List.class, ConfluencePageDto.class);
        return objectMapper.convertValue(results, pageDtoCollectionType);
    }

    private List<ConfluencePage> enrichPagesWithSpaceKey(List<ConfluencePageDto> pageDtos, String spaceKey) {
        var spaceDto = ConfluencePageDto.SpaceDto.builder().key(spaceKey).build();
        return pageDtos.stream()
            .map(page -> page.toBuilder().space(spaceDto).build())
            .map(ConfluencePageMapper::toDomain)
            .toList();
    }

    private boolean shouldFetchNextBatch(List<ConfluencePage> currentBatch, int pageSize) {
        return currentBatch.size() == pageSize;
    }

    private CompletableFuture<List<ConfluencePage>> fetchNextBatchAndMerge(
        List<ConfluencePage> currentBatch, String spaceKey, int startIndex, int pageSize, int remainingPagesLimit) {
        
        return collectAllPagesRecursively(spaceKey, startIndex + pageSize, pageSize, remainingPagesLimit - 1)
            .thenApply(nextBatch -> mergePageBatches(currentBatch, nextBatch));
    }

    private List<ConfluencePage> mergePageBatches(List<ConfluencePage> currentBatch, List<ConfluencePage> nextBatch) {
        var merged = new ArrayList<ConfluencePage>(currentBatch.size() + nextBatch.size());
        merged.addAll(currentBatch);
        merged.addAll(nextBatch);
        return merged;
    }

    private String serializePageDto(ConfluencePageDto pageDto) throws JsonProcessingException {
        return objectMapper.writeValueAsString(pageDto);
    }

    private HttpRequest buildUpdatePageRequest(String pageId, String requestBody) {
        var uri = urlBuilder.buildUpdatePageUri(pageId);
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER_NAME, authHeader)
            .header("Content-Type", CONTENT_TYPE_HEADER_VALUE)
            .header(ACCEPT_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE)
            .timeout(Duration.ofMillis(config.readTimeout()))
            .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    }

    private ConfluencePage parseUpdatedPage(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new ConfluenceApiException(
                "Erreur lors de la mise à jour", response.statusCode(), response.body());
        }
        return responseParser.deserializeUpdatedPage(response.body());
    }

    private HttpRequest buildSpaceRequest(String spaceKeyOrId) {
        var uri = urlBuilder.buildSpaceUri(spaceKeyOrId);
        return buildGetRequest(uri);
    }

    private HttpRequest buildSpaceRequestWithPermissions(String spaceKey) {
        var uri = urlBuilder.buildSpaceUriWithPermissions(spaceKey);
        return buildGetRequest(uri);
    }

    private Optional<ConfluenceSpace> parseSpaceResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return Optional.empty();
        }
        return responseParser.deserializeSpaceDto(response.body());
    }

    private HttpRequest buildConnectionTestRequest() {
        var uri = urlBuilder.buildConnectionTestUri();
        return HttpRequest.newBuilder()
            .uri(uri)
            .header(AUTHORIZATION_HEADER_NAME, authHeader)
            .header(ACCEPT_HEADER_NAME, CONTENT_TYPE_HEADER_VALUE)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    }

    private boolean isConnectionSuccessful(HttpResponse<String> response) {
        var isConnected = response.statusCode() == 200;
        log.info("Test de connexion: {}", isConnected ? "Réussi" : "Échoué");
        return isConnected;
    }

    private boolean handleConnectionError(Throwable ex) {
        log.error("Erreur lors du test de connexion", ex);
        return false;
    }

    private CompletableFuture<List<ConfluenceSpace>> retrieveAllSpaces() {
        return collectAllSpacesRecursively(0, 250, List.of())
            .thenApply(responseParser::convertSpaceDtosToSpaces);
    }

    private CompletableFuture<List<ConfluenceSpaceDto>> collectAllSpacesRecursively(
        int startIndex, int pageSize, List<ConfluenceSpaceDto> accumulated) {
        
        var request = buildAllSpacesRequest(startIndex, pageSize);
        return retryExecutor.executeRequest(request)
            .thenCompose(response -> processSpacesBatch(response, startIndex, pageSize, accumulated));
    }

    private HttpRequest buildAllSpacesRequest(int startIndex, int pageSize) {
        var uri = urlBuilder.buildAllSpacesUri(startIndex, pageSize);
        return buildGetRequest(uri);
    }

    private CompletableFuture<List<ConfluenceSpaceDto>> processSpacesBatch(
        HttpResponse<String> response, int startIndex, int pageSize, List<ConfluenceSpaceDto> accumulated) {
        
        if (response.statusCode() != 200) {
            log.error("Erreur HTTP {} lors de la récupération des espaces (start={})",
                response.statusCode(), startIndex);
            return CompletableFuture.completedFuture(accumulated);
        }

        try {
            var spacesResponse = responseParser.deserializeSpacesResponse(response.body());
            var currentBatch = extractSpaceDtos(spacesResponse);
            var mergedSpaces = mergeSpaceBatches(accumulated, currentBatch);

            if (paginationHandler.shouldFetchNextSpaceBatch(spacesResponse, currentBatch, pageSize)) {
                var effectiveLimit = paginationHandler.calculateEffectivePageSize(spacesResponse, currentBatch, pageSize);
                return collectAllSpacesRecursively(startIndex + effectiveLimit, pageSize, mergedSpaces);
            }

            log.info("Fin pagination espaces: total={} (start dernier={})", mergedSpaces.size(), startIndex);
            return CompletableFuture.completedFuture(mergedSpaces);
        } catch (Exception e) {
            log.error("Erreur lors de la désérialisation des espaces (start={})", startIndex, e);
            return CompletableFuture.completedFuture(accumulated);
        }
    }

    private List<ConfluenceSpaceDto> extractSpaceDtos(ConfluenceSpacesResponseDto response) {
        var results = response.results();
        return results != null ? results : List.of();
    }

    private List<ConfluenceSpaceDto> mergeSpaceBatches(
        List<ConfluenceSpaceDto> accumulated, List<ConfluenceSpaceDto> currentBatch) {
        
        if (currentBatch.isEmpty()) {
            return accumulated;
        }
        
        var merged = new ArrayList<ConfluenceSpaceDto>(accumulated.size() + currentBatch.size());
        merged.addAll(accumulated);
        merged.addAll(currentBatch);
        return merged;
    }

    private String formatInstantForCql(Instant instant) {
        return CQL_DATE_TIME_FORMATTER.format(instant);
    }

    private HttpRequest buildContentSearchModifiedSinceRequest(String spaceKey, String sinceDate) {
        var uri = urlBuilder.buildContentSearchModifiedSinceUri(spaceKey, sinceDate);
        return buildGetRequest(uri);
    }

    /**
     * Extracts page modification date from Confluence API response.
     * Tries version.when first, then falls back to history.lastUpdated.when.
     *
     * @param page JSON node containing page data from Confluence API
     * @return modification date as Instant, or null if not found or unparseable
     */
    private Instant extractPageModificationDate(JsonNode page) {
        return tryExtractFromVersionWhen(page)
            .or(() -> tryExtractFromHistoryWhen(page))
            .orElse(null);
    }

    /**
     * Attempts to extract modification date from version.when field.
     *
     * @param page JSON node containing page data
     * @return Optional containing the parsed Instant, or empty if not found or unparseable
     */
    private Optional<Instant> tryExtractFromVersionWhen(JsonNode page) {
        return Optional.ofNullable(page.get("version"))
            .flatMap(version -> Optional.ofNullable(version.get("when")))
            .flatMap(when -> parseInstantSafely(when, "version.when", 
                "Will attempt fallback to history.lastUpdated.when"));
    }

    /**
     * Attempts to extract modification date from history.lastUpdated.when field.
     *
     * @param page JSON node containing page data
     * @return Optional containing the parsed Instant, or empty if not found or unparseable
     */
    private Optional<Instant> tryExtractFromHistoryWhen(JsonNode page) {
        return Optional.ofNullable(page.get("history"))
            .flatMap(history -> Optional.ofNullable(history.get("lastUpdated")))
            .flatMap(lastUpdated -> Optional.ofNullable(lastUpdated.get("when")))
            .flatMap(when -> parseInstantSafely(when, "history.lastUpdated.when",
                "Entry will be ignored. This may indicate a change in the API format."));
    }

    /**
     * Safely parses an Instant from a JsonNode, handling parsing exceptions.
     *
     * @param whenNode JSON node containing the date string
     * @param fieldName name of the field for logging purposes
     * @param additionalMessage additional context message for error logging
     * @return Optional containing the parsed Instant, or empty if parsing fails
     */
    private Optional<Instant> parseInstantSafely(JsonNode whenNode, String fieldName, String additionalMessage) {
        try {
            return Optional.of(parseInstant(whenNode.asText()));
        } catch (ConfluenceDateParseException e) {
            log.error("Failed to parse modification date from {} field in Confluence response. {}", 
                fieldName, additionalMessage, e);
            return Optional.empty();
        }
    }

    private Instant parseInstant(String dateString) {
        try {
            return Instant.parse(dateString);
        } catch (Exception e) {
            throw new ConfluenceDateParseException(dateString, e);
        }
    }

    @Override
    public CompletableFuture<List<ModifiedPageInfo>> getModifiedPagesSince(
        String spaceKey, Instant sinceDate) {
        
        log.debug("Getting modified pages for space {} since {}", spaceKey, sinceDate);
        
        var sinceDateFormatted = formatInstantForCql(sinceDate);
        var request = buildContentSearchModifiedSinceRequest(spaceKey, sinceDateFormatted);
        
        return retryExecutor.executeRequest(request)
            .thenApply(this::extractModifiedPagesInfo)
            .exceptionally(ex -> handleModifiedPagesError(ex, spaceKey));
    }

    private List<ModifiedPageInfo> extractModifiedPagesInfo(
        HttpResponse<String> response) {
        
        if (response.statusCode() != 200) {
            log.warn("CQL Search returned non-200 status: {}", response.statusCode());
            return List.of();
        }

        try {
            var jsonNode = objectMapper.readTree(response.body());
            
            if (!jsonNode.has(RESULTS_FIELD)) {
                return List.of();
            }

            var results = jsonNode.get(RESULTS_FIELD);
            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }

            return extractPageInfoList(results);
            
        } catch (IOException e) {
            log.error("Error parsing CQL search response", e);
            return List.of();
        }
    }

    private List<ModifiedPageInfo> extractPageInfoList(
        JsonNode results) {
        
        var pageInfoList = new ArrayList<ModifiedPageInfo>();

        for (var page : results) {
            var pageInfo = extractSinglePageInfo(page);
            if (pageInfo != null) {
                pageInfoList.add(pageInfo);
            }
        }

        return pageInfoList;
    }

    private ModifiedPageInfo extractSinglePageInfo(
        JsonNode page) {
        
        try {
            String type = page.has(TYPE_FIELD_NAME) ? page.get(TYPE_FIELD_NAME).asText() : null;
            if (!PAGE_FIELD_NAME.equalsIgnoreCase(type)) {
                return null;
            }

            String pageId = page.has(ID_FIELD_NAME) ? page.get(ID_FIELD_NAME).asText() : null;
            String title = page.has(TITLE_FIELD_NAME) ? page.get(TITLE_FIELD_NAME).asText() : "Untitled";
            Instant lastModified = extractPageModificationDate(page);
            
            if (pageId == null || lastModified == null) {
                return null;
            }
            
            return new ModifiedPageInfo(
                pageId,
                title,
                lastModified
            );
        } catch (Exception e) {
            log.warn("Failed to extract page info from JSON node", e);
            return null;
        }
    }

    private List<ModifiedPageInfo> handleModifiedPagesError(
        Throwable ex, String spaceKey) {
        
        log.error("Error retrieving modified pages for space {}", spaceKey, ex);
        return List.of();
    }

    @Override
    public CompletableFuture<List<ModifiedAttachmentInfo>> getModifiedAttachmentsSince(
        String spaceKey, Instant sinceDate) {

        log.debug("Getting modified attachments for space {} since {}", spaceKey, sinceDate);

        var sinceDateFormatted = formatInstantForCql(sinceDate);
        var cql = String.format("space='%s' AND type=attachment AND lastmodified >= '%s'",
                                spaceKey, sinceDateFormatted);
        var request = buildGetRequest(urlBuilder.buildSearchUri(cql));

        return retryExecutor.executeRequest(request)
            .thenApply(this::extractModifiedAttachmentsInfo)
            .exceptionally(ex -> {
                log.error("Error retrieving modified attachments for space {}", spaceKey, ex);
                return List.of();
            });
    }

    private List<ModifiedAttachmentInfo> extractModifiedAttachmentsInfo(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            log.warn("CQL Search (attachments) returned non-200 status: {}", response.statusCode());
            return List.of();
        }
        try {
            var jsonNode = objectMapper.readTree(response.body());
            if (!jsonNode.has(RESULTS_FIELD)) {
                return List.of();
            }
            var results = jsonNode.get(RESULTS_FIELD);
            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }
            var modifiedAttachmentInfos = new ArrayList<ModifiedAttachmentInfo>(results.size());
            for (var node : results) {
                var info = extractSingleAttachmentInfo(node);
                if (info != null) {
                    modifiedAttachmentInfos.add(info);
                }
            }
            return modifiedAttachmentInfos;
        } catch (IOException e) {
            log.error("Error parsing attachment CQL search response", e);
            return List.of();
        }
    }

    private ModifiedAttachmentInfo extractSingleAttachmentInfo(JsonNode node) {
        try {
            String id = node.has(ID_FIELD_NAME) ? node.get(ID_FIELD_NAME).asText() : null;
            String title = node.has(TITLE_FIELD_NAME) ? node.get(TITLE_FIELD_NAME).asText() : "Unnamed";
            Instant lastModified = extractPageModificationDate(node);
            if (id == null || lastModified == null) {
                return null;
            }
            return new ModifiedAttachmentInfo(id, title, lastModified);
        } catch (Exception e) {
            log.warn("Failed to extract attachment info from JSON node", e);
            return null;
        }
    }
}
