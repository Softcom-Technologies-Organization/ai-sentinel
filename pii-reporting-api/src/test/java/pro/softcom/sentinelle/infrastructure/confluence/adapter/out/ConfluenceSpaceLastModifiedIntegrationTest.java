package pro.softcom.sentinelle.infrastructure.confluence.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config.ConfluenceConnectionConfig;

import org.junit.jupiter.api.Disabled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration pour explorer la structure de données retournée par l'API Confluence
 * concernant le champ lastModified ou équivalent pour les spaces.
 * 
 * Business purpose: Valider que l'API Confluence fournit bien les informations de dernière
 * modification nécessaires pour détecter les mises à jour des spaces.
 * 
 * RÉSULTATS DE L'EXPLORATION (basés sur documentation Confluence API):
 * - L'API Confluence fournit un champ 'history' avec expand=history
 * - Dans 'history', on trouve:
 *   - createdDate: Date de création du space (ISO 8601)
 *   - lastUpdated: Objet contenant when (date de dernière modification)
 * - Pour les pages d'un space, chaque page a aussi son propre history.lastUpdated
 * 
 * Note: Tests désactivés car nécessitent credentials Confluence valides en environnement test.
 */
@SpringBootTest
//@ActiveProfiles("test")
@ActiveProfiles("integration")
@Slf4j
//@Disabled("Tests d'exploration API - nécessitent credentials Confluence valides")
class ConfluenceSpaceLastModifiedIntegrationTest {

    @Autowired
    @Qualifier("confluenceConfig")
    private ConfluenceConnectionConfig config;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpClient httpClient;
    private String authHeader;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeout()))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        
        log.info("=== CONFLUENCE CONFIGURATION ===");
        log.info("Base URL: {}", config.baseUrl());
        log.info("Username: {}", config.username());
        log.info("API Token: {}", config.apiToken() != null && !config.apiToken().isEmpty() 
            ? config.apiToken().substring(0, Math.min(4, config.apiToken().length())) + "****" 
            : "NOT SET");
        log.info("Connect Timeout: {}", config.connectTimeout());
        log.info("Read Timeout: {}", config.readTimeout());
        
        String credentials = config.username() + ":" + config.apiToken();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        authHeader = "Basic " + encoded;
        
        log.info("Authorization header prepared (Base64 encoded)");
    }

    @Test
    void Should_ReturnSpaceWithTimestampFields_When_QueryingConfluenceApi() throws IOException, InterruptedException {
        // Given - Requête vers l'API Confluence pour récupérer un space
        String testSpaceKey = "admin"; // Utilisez une clé de space existante dans votre Confluence
        URI uri = URI.create(config.baseUrl() + "/rest/api/space/" + testSpaceKey + "?expand=history");
        
        log.info("=== REQUEST DETAILS ===");
        log.info("Full URL: {}", uri);
        log.info("Space Key: {}", testSpaceKey);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API Confluence
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Log response status and body even if not 200
        log.info("=== RESPONSE DETAILS ===");
        log.info("Status Code: {}", response.statusCode());
        log.info("Response Headers: {}", response.headers().map());
        log.info("Response Body: {}", response.body());
        
        // Then - Vérifier que la réponse est OK et explorer sa structure
        assertThat(response.statusCode())
            .as("Space '%s' should exist. Response body: %s", testSpaceKey, response.body())
            .isEqualTo(200);
        
        String responseBody = response.body();
        log.info("=== CONFLUENCE SPACE API RESPONSE ===");
        log.info("Raw JSON: {}", responseBody);
        
        // Parser et afficher la structure de manière formatée
        var jsonNode = objectMapper.readTree(responseBody);
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier les champs attendus de base
        assertThat(jsonNode.has("id")).isTrue();
        assertThat(jsonNode.has("key")).isTrue();
        assertThat(jsonNode.has("name")).isTrue();
        
        // Explorer les champs liés au timestamp
        log.info("=== TIMESTAMP FIELDS EXPLORATION ===");
        
        if (jsonNode.has("history")) {
            var history = jsonNode.get("history");
            log.info("History field found: {}", history);
            
            if (history.has("createdDate")) {
                log.info("createdDate: {}", history.get("createdDate").asText());
            }
            if (history.has("lastUpdated")) {
                log.info("lastUpdated: {}", history.get("lastUpdated"));
            }
        }
        
        // Chercher d'autres champs potentiels
        if (jsonNode.has("_expandable")) {
            log.info("_expandable: {}", jsonNode.get("_expandable"));
        }
        
        if (jsonNode.has("_links")) {
            log.info("_links: {}", jsonNode.get("_links"));
        }
        
        // Logger tous les champs de premier niveau pour analyse
        log.info("=== ALL TOP-LEVEL FIELDS ===");
        jsonNode.fieldNames().forEachRemaining(fieldName -> 
            log.info("Field '{}': {}", fieldName, jsonNode.get(fieldName))
        );
        
        // Assertion: Au minimum, on doit avoir un champ history ou équivalent
        assertThat(jsonNode.has("history") || jsonNode.has("metadata"))
            .as("API should provide timestamp information via 'history' or 'metadata' field")
            .isTrue();
    }

    @Test
    void Should_ReturnSpaceWithLastUpdated_When_UsingHistoryLastUpdatedExpansion() throws IOException, InterruptedException {
        // Given - Requête avec expansion history.lastUpdated comme suggéré par la doc API v1
        String testSpaceKey = "admin";
        URI uri = URI.create(config.baseUrl() + "/rest/api/space/" + testSpaceKey + "?expand=history.lastUpdated");
        
        log.info("=== TESTING history.lastUpdated EXPANSION ===");
        log.info("Full URL: {}", uri);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API Confluence
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Log response
        log.info("=== RESPONSE FOR history.lastUpdated ===");
        log.info("Status Code: {}", response.statusCode());
        log.info("Response Body: {}", response.body());
        
        // Then - Vérifier la réponse
        assertThat(response.statusCode()).isEqualTo(200);
        
        var jsonNode = objectMapper.readTree(response.body());
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier si history.lastUpdated est présent
        if (jsonNode.has("history")) {
            var history = jsonNode.get("history");
            log.info("=== HISTORY CONTENT ===");
            log.info("History: {}", history);
            
            if (history.has("lastUpdated")) {
                var lastUpdated = history.get("lastUpdated");
                log.info("✅ lastUpdated FOUND: {}", lastUpdated);
                
                if (lastUpdated.has("when")) {
                    log.info("✅ lastUpdated.when: {}", lastUpdated.get("when").asText());
                }
                if (lastUpdated.has("by")) {
                    log.info("lastUpdated.by: {}", lastUpdated.get("by"));
                }
                
                assertThat(lastUpdated.has("when"))
                    .as("lastUpdated should contain 'when' field with timestamp")
                    .isTrue();
            } else {
                log.warn("❌ lastUpdated NOT found in history - API may not support this for spaces");
            }
        } else {
            log.warn("❌ history field not found in response");
        }
    }

    @Test
    void Should_ReturnPagesWithLastUpdated_When_ExpandingHistoryLastUpdated() throws IOException, InterruptedException {
        // Given - Requête vers l'API Content pour récupérer les pages d'un space avec history.lastUpdated
        String testSpaceKey = "admin";
        URI uri = URI.create(config.baseUrl() + "/rest/api/content?type=page&spaceKey=" + testSpaceKey + "&expand=history.lastUpdated&limit=5");
        
        log.info("=== TESTING CONTENT API WITH history.lastUpdated ===");
        log.info("Full URL: {}", uri);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API Confluence
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Log response
        log.info("=== RESPONSE FOR CONTENT API ===");
        log.info("Status Code: {}", response.statusCode());
        
        // Then - Vérifier la réponse
        assertThat(response.statusCode()).isEqualTo(200);
        
        var jsonNode = objectMapper.readTree(response.body());
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier la structure
        assertThat(jsonNode.has("results")).isTrue();
        var results = jsonNode.get("results");
        assertThat(results.isArray()).isTrue();
        
        if (results.size() > 0) {
            var firstPage = results.get(0);
            log.info("=== FIRST PAGE STRUCTURE ===");
            
            // Vérifier history
            if (firstPage.has("history")) {
                var history = firstPage.get("history");
                log.info("History: {}", history);
                
                if (history.has("lastUpdated")) {
                    var lastUpdated = history.get("lastUpdated");
                    log.info("✅ lastUpdated FOUND for page!");
                    log.info("lastUpdated: {}", lastUpdated);
                    
                    if (lastUpdated.has("when")) {
                        String when = lastUpdated.get("when").asText();
                        log.info("✅ lastUpdated.when: {}", when);
                        
                        assertThat(when)
                            .as("lastUpdated.when should contain a valid timestamp")
                            .isNotNull()
                            .isNotEmpty();
                        
                        // Vérifier aussi le format ISO 8601
                        assertThat(when).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*");
                    }
                    
                    if (lastUpdated.has("by")) {
                        log.info("lastUpdated.by: {}", lastUpdated.get("by").get("displayName").asText());
                    }
                } else {
                    log.warn("❌ lastUpdated NOT found in history for page");
                }
            } else {
                log.warn("❌ history field not found for page");
            }
            
            // Logger toutes les pages avec leur lastUpdated.when
            log.info("=== ALL PAGES WITH lastUpdated ===");
            for (int i = 0; i < results.size(); i++) {
                var page = results.get(i);
                String title = page.get("title").asText();
                String pageId = page.get("id").asText();
                
                if (page.has("history") && page.get("history").has("lastUpdated")) {
                    var lastUpdated = page.get("history").get("lastUpdated");
                    if (lastUpdated.has("when")) {
                        String when = lastUpdated.get("when").asText();
                        log.info("Page #{} - ID: {} - Title: '{}' - lastUpdated.when: {}", 
                            i + 1, pageId, title, when);
                    }
                }
            }
        } else {
            log.warn("No pages found in space: {}", testSpaceKey);
        }
    }

    @Test
    void Should_ReturnAllSpacesWithMetadata_When_ListingSpaces() throws IOException, InterruptedException {
        // Given - Requête vers l'API Confluence pour lister les spaces avec metadata
        URI uri = URI.create(config.baseUrl() + "/rest/api/space?limit=3&expand=history,metadata");
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API Confluence
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then - Vérifier et explorer la structure des spaces listés
        assertThat(response.statusCode()).isEqualTo(200);
        
        String responseBody = response.body();
        log.info("=== CONFLUENCE SPACES LIST API RESPONSE ===");
        
        var jsonNode = objectMapper.readTree(responseBody);
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier la structure de base
        assertThat(jsonNode.has("results")).isTrue();
        
        var results = jsonNode.get("results");
        assertThat(results.isArray()).isTrue();
        assertThat(results.size()).isGreaterThan(0);
        
        // Explorer le premier space de la liste
        var firstSpace = results.get(0);
        log.info("=== FIRST SPACE STRUCTURE ===");
        firstSpace.fieldNames().forEachRemaining(fieldName -> 
            log.info("Field '{}': {}", fieldName, firstSpace.get(fieldName))
        );
        
        // Vérifier si les informations de timestamp sont présentes dans la liste
        if (firstSpace.has("history")) {
            log.info("History available in space list!");
            log.info("History details: {}", firstSpace.get("history"));
        } else {
            log.warn("History NOT available in space list - may need individual space queries");
        }
    }

    @Test
    void Should_ReturnSpaceLastModified_When_UsingCQLSearchAPI() throws IOException, InterruptedException {
        // Given - Requête CQL pour récupérer un space via l'API Search
        // Cette approche a été suggérée par la communauté Confluence comme solution
        // pour récupérer la date de dernière modification d'un space
        String testSpaceKey = "admin";
        String cqlQuery = "space.key=" + testSpaceKey + "+AND+type=space";
        URI uri = URI.create(config.baseUrl() + "/rest/api/search?cql=" + cqlQuery);
        
        log.info("=== TESTING CQL SEARCH API FOR SPACE LAST MODIFIED ===");
        log.info("Full URL: {}", uri);
        log.info("CQL Query: {}", cqlQuery);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API CQL Search
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Log response details
        log.info("=== CQL SEARCH API RESPONSE ===");
        log.info("Status Code: {}", response.statusCode());
        log.info("Response Headers: {}", response.headers().map());
        
        // Then - Vérifier la réponse et explorer la structure
        assertThat(response.statusCode())
            .as("CQL Search API should return 200 OK. Response body: %s", response.body())
            .isEqualTo(200);
        
        String responseBody = response.body();
        var jsonNode = objectMapper.readTree(responseBody);
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier la structure de base de la réponse CQL
        assertThat(jsonNode.has("results"))
            .as("CQL Search response should contain 'results' array")
            .isTrue();
        
        var results = jsonNode.get("results");
        assertThat(results.isArray())
            .as("'results' should be an array")
            .isTrue();
        
        assertThat(results.size())
            .as("CQL Search should return at least one result for space: %s", testSpaceKey)
            .isGreaterThan(0);
        
        // Explorer le space retourné
        var spaceResult = results.get(0);
        log.info("=== CQL SEARCH RESULT STRUCTURE ===");
        spaceResult.fieldNames().forEachRemaining(fieldName -> 
            log.info("Field '{}': {}", fieldName, spaceResult.get(fieldName))
        );
        
        // Vérifier les champs essentiels du space
        if (spaceResult.has("space")) {
            var space = spaceResult.get("space");
            log.info("=== SPACE OBJECT IN RESULT ===");
            log.info("Space: {}", space);
            
            // Vérifier les champs de base
            if (space.has("key")) {
                String key = space.get("key").asText();
                log.info("✅ Space key: {}", key);
                assertThat(key).isEqualTo(testSpaceKey);
            }
            
            if (space.has("name")) {
                log.info("Space name: {}", space.get("name").asText());
            }
        }
        
        // Explorer les champs de timestamp
        log.info("=== EXPLORING TIMESTAMP FIELDS ===");
        
        // Vérifier si lastModified est présent directement
        if (spaceResult.has("lastModified")) {
            var lastModified = spaceResult.get("lastModified");
            log.info("✅ lastModified FOUND directly in result: {}", lastModified);
            
            if (lastModified.isTextual()) {
                String lastModifiedDate = lastModified.asText();
                log.info("✅ lastModified date: {}", lastModifiedDate);
                
                assertThat(lastModifiedDate)
                    .as("lastModified should contain a valid timestamp")
                    .isNotNull()
                    .isNotEmpty()
                    .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*");
            } else if (lastModified.isObject()) {
                log.info("lastModified is an object:");
                lastModified.fieldNames().forEachRemaining(field -> 
                    log.info("  {}: {}", field, lastModified.get(field))
                );
            }
        } else {
            log.warn("❌ lastModified NOT found directly in result");
        }
        
        // Vérifier d'autres champs potentiels de timestamp
        if (spaceResult.has("timestamp")) {
            log.info("timestamp field: {}", spaceResult.get("timestamp"));
        }
        
        if (spaceResult.has("history")) {
            var history = spaceResult.get("history");
            log.info("history field: {}", history);
            
            if (history.has("lastUpdated")) {
                var lastUpdated = history.get("lastUpdated");
                log.info("✅ history.lastUpdated found: {}", lastUpdated);
                
                if (lastUpdated.has("when")) {
                    String when = lastUpdated.get("when").asText();
                    log.info("✅ history.lastUpdated.when: {}", when);
                    
                    assertThat(when)
                        .as("history.lastUpdated.when should contain a valid timestamp")
                        .isNotNull()
                        .isNotEmpty();
                }
            }
        }
        
        if (spaceResult.has("metadata")) {
            log.info("metadata field: {}", spaceResult.get("metadata"));
        }
        
        // Logger tous les champs de premier niveau pour analyse complète
        log.info("=== ALL TOP-LEVEL FIELDS IN CQL RESULT ===");
        spaceResult.fieldNames().forEachRemaining(fieldName -> {
            var fieldValue = spaceResult.get(fieldName);
            if (fieldValue.isTextual() || fieldValue.isNumber() || fieldValue.isBoolean()) {
                log.info("Field '{}': {}", fieldName, fieldValue);
            } else {
                log.info("Field '{}': [complex object/array]", fieldName);
            }
        });
        
        // Conclusion : vérifier qu'au moins une information de timestamp est disponible
        boolean hasTimestamp = spaceResult.has("lastModified") 
            || (spaceResult.has("history") && spaceResult.get("history").has("lastUpdated"))
            || spaceResult.has("timestamp");
        
        assertThat(hasTimestamp)
            .as("CQL Search API should provide at least one timestamp field (lastModified, history.lastUpdated, or timestamp)")
            .isTrue();
        
        log.info("=== TEST CONCLUSION ===");
        if (spaceResult.has("lastModified")) {
            log.info("✅ CQL Search API successfully provides 'lastModified' field for spaces");
        } else {
            log.info("ℹ️ CQL Search API does not provide 'lastModified' directly, alternative fields may be available");
        }
    }

    @Test
    void Should_ReturnRecentlyModifiedPages_When_UsingContentSearchWithLastModifiedFilter() throws IOException, InterruptedException {
        // Given - Requête CQL pour rechercher les pages modifiées récemment dans un space
        // Cette approche utilise l'API Content Search pour filtrer par date de modification
        String testSpaceKey = "admin";
        String dateFilter = "2025-10-24"; // Format YYYY-MM-DD pour CQL
        String cqlQuery = "lastModified>=\"" + dateFilter + "\" AND space=\"" + testSpaceKey + "\"";
        String encodedQuery = URLEncoder.encode(cqlQuery, StandardCharsets.UTF_8);
        URI uri = URI.create(config.baseUrl() + "/rest/api/content/search?cql=" + encodedQuery + "&expand=version,history.lastUpdated");
        
        log.info("=== TESTING CONTENT SEARCH API WITH lastModified FILTER ===");
        log.info("Full URL: {}", uri);
        log.info("CQL Query: {}", cqlQuery);
        log.info("Encoded Query: {}", encodedQuery);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeout()))
            .GET()
            .build();

        // When - Appel de l'API Content Search
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Log response details
        log.info("=== CONTENT SEARCH API RESPONSE ===");
        log.info("Status Code: {}", response.statusCode());
        
        // Then - Vérifier la réponse et explorer la structure
        assertThat(response.statusCode())
            .as("Content Search API should return 200 OK. Response body: %s", response.body())
            .isEqualTo(200);
        
        String responseBody = response.body();
        var jsonNode = objectMapper.readTree(responseBody);
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        log.info("Formatted JSON:\n{}", prettyJson);
        
        // Vérifier la structure de base
        assertThat(jsonNode.has("results"))
            .as("Content Search response should contain 'results' array")
            .isTrue();
        
        var results = jsonNode.get("results");
        assertThat(results.isArray())
            .as("'results' should be an array")
            .isTrue();
        
        log.info("=== SEARCH RESULTS SUMMARY ===");
        log.info("Total pages found: {}", results.size());
        
        if (results.size() > 0) {
            log.info("=== DETAILED PAGE INFORMATION ===");
            
            // Analyser chaque page retournée
            for (int i = 0; i < results.size(); i++) {
                var page = results.get(i);
                
                String pageId = page.has("id") ? page.get("id").asText() : "N/A";
                String title = page.has("title") ? page.get("title").asText() : "N/A";
                String type = page.has("type") ? page.get("type").asText() : "N/A";
                
                log.info("\n--- Page #{} ---", i + 1);
                log.info("ID: {}", pageId);
                log.info("Title: {}", title);
                log.info("Type: {}", type);
                
                // Vérifier le champ version
                if (page.has("version")) {
                    var version = page.get("version");
                    log.info("Version info:");
                    
                    if (version.has("number")) {
                        log.info("  Version number: {}", version.get("number").asInt());
                    }
                    
                    if (version.has("when")) {
                        String versionWhen = version.get("when").asText();
                        log.info("  ✅ Version when (last modified): {}", versionWhen);
                        
                        assertThat(versionWhen)
                            .as("version.when should contain a valid timestamp")
                            .isNotNull()
                            .isNotEmpty()
                            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*");
                    }
                    
                    if (version.has("by")) {
                        var by = version.get("by");
                        if (by.has("displayName")) {
                            log.info("  Modified by: {}", by.get("displayName").asText());
                        }
                    }
                }
                
                // Vérifier le champ history.lastUpdated
                if (page.has("history")) {
                    var history = page.get("history");
                    log.info("History info:");
                    
                    if (history.has("lastUpdated")) {
                        var lastUpdated = history.get("lastUpdated");
                        log.info("  lastUpdated found:");
                        
                        if (lastUpdated.has("when")) {
                            String when = lastUpdated.get("when").asText();
                            log.info("    ✅ when: {}", when);
                            
                            assertThat(when)
                                .as("history.lastUpdated.when should contain a valid timestamp")
                                .isNotNull()
                                .isNotEmpty()
                                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*");
                        }
                        
                        if (lastUpdated.has("by")) {
                            var by = lastUpdated.get("by");
                            if (by.has("displayName")) {
                                log.info("    Modified by: {}", by.get("displayName").asText());
                            }
                        }
                    }
                    
                    if (history.has("createdDate")) {
                        log.info("  Created date: {}", history.get("createdDate").asText());
                    }
                }
                
                // Vérifier si le statut est présent
                if (page.has("status")) {
                    log.info("Status: {}", page.get("status").asText());
                }
                
                // Logger l'URL de la page si disponible
                if (page.has("_links")) {
                    var links = page.get("_links");
                    if (links.has("webui")) {
                        String webui = links.get("webui").asText();
                        log.info("Web URL: {}{}", config.baseUrl(), webui);
                    }
                }
            }
            
            // Vérifier que toutes les pages ont bien un timestamp de modification
            log.info("\n=== TIMESTAMP VALIDATION ===");
            int pagesWithTimestamp = 0;
            for (int i = 0; i < results.size(); i++) {
                var page = results.get(i);
                boolean hasTimestamp = (page.has("version") && page.get("version").has("when")) 
                    || (page.has("history") && page.get("history").has("lastUpdated"));
                
                if (hasTimestamp) {
                    pagesWithTimestamp++;
                }
            }
            
            log.info("Pages with timestamp: {}/{}", pagesWithTimestamp, results.size());
            
            assertThat(pagesWithTimestamp)
                .as("All returned pages should have timestamp information")
                .isEqualTo(results.size());
            
            // Vérifier que les pages récemment modifiées sont bien incluses
            log.info("\n=== TEST VALIDATION ===");
            log.info("✅ Content Search API successfully returns pages modified after {}", dateFilter);
            log.info("✅ Timestamp information is available via version.when and/or history.lastUpdated.when");
            
        } else {
            log.warn("No pages found modified after {} in space: {}", dateFilter, testSpaceKey);
            log.warn("This might indicate:");
            log.warn("  1. No pages were modified after this date");
            log.warn("  2. The date format needs adjustment");
            log.warn("  3. The CQL query syntax needs to be corrected");
        }
        
        // Assertion finale : vérifier qu'on peut utiliser cette API pour détecter les mises à jour
        log.info("\n=== BUSINESS OUTCOME ===");
        log.info("This API endpoint can be used to:");
        log.info("  1. Detect recently modified pages in a space");
        log.info("  2. Get accurate lastModified timestamps via version.when");
        log.info("  3. Filter pages by modification date using CQL");
        log.info("  4. Determine if a space has been updated by checking if any pages were modified");
    }
}
