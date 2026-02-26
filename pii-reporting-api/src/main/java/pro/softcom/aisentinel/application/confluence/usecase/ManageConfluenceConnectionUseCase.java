package pro.softcom.aisentinel.application.confluence.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.softcom.aisentinel.application.confluence.port.in.ManageConfluenceConnectionPort;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceConnectionConfigRepository;
import pro.softcom.aisentinel.domain.confluence.ConfluenceConnectionSettings;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Use case for managing Confluence connection configuration.
 * Handles retrieval, update, and connection testing.
 */
public class ManageConfluenceConnectionUseCase implements ManageConfluenceConnectionPort {

    private static final Logger log = LoggerFactory.getLogger(ManageConfluenceConnectionUseCase.class);
    private static final Integer CONFIG_ID = 1;
    private static final EncryptionMetadata TOKEN_METADATA = new EncryptionMetadata("CONFLUENCE_API_TOKEN", 0, 0);

    private final ConfluenceConnectionConfigRepository repository;
    private final EncryptionService encryptionService;
    private final Runnable onConfigUpdated;

    public ManageConfluenceConnectionUseCase(ConfluenceConnectionConfigRepository repository,
                                             EncryptionService encryptionService,
                                             Runnable onConfigUpdated) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.onConfigUpdated = onConfigUpdated;
    }

    @Override
    public ConfluenceConnectionSettings getConnectionSettings() {
        log.debug("Retrieving Confluence connection settings");
        return repository.findConfig();
    }

    @Override
    public boolean isConfigured() {
        String encryptedToken = repository.getEncryptedApiToken();
        return encryptedToken != null && !encryptedToken.isBlank();
    }

    @Override
    public ConfluenceConnectionSettings updateConnectionSettings(UpdateConfluenceConnectionCommand command) {
        log.info("Updating Confluence connection settings: baseUrl={}, username={}, connectTimeout={}, " +
                        "readTimeout={}, maxRetries={}, pagesLimit={}, maxPages={}",
                command.baseUrl(), command.username(), command.connectTimeout(),
                command.readTimeout(), command.maxRetries(), command.pagesLimit(), command.maxPages());

        ConfluenceConnectionSettings newSettings = new ConfluenceConnectionSettings(
                CONFIG_ID,
                command.baseUrl(),
                command.username(),
                command.connectTimeout(),
                command.readTimeout(),
                command.maxRetries(),
                command.pagesLimit(),
                command.maxPages(),
                Instant.now(),
                command.updatedBy()
        );

        // If API token is not provided (empty/blank), keep the existing token
        String encryptedToken;
        if (command.apiToken() == null || command.apiToken().isBlank()) {
            log.debug("No API token provided, keeping existing token");
            encryptedToken = repository.getEncryptedApiToken();
        } else {
            log.debug("Encrypting new API token");
            encryptedToken = encryptToken(command.apiToken());
        }

        repository.updateConfig(newSettings, encryptedToken);
        onConfigUpdated.run();

        log.info("Confluence connection settings updated successfully by user: {}", command.updatedBy());
        return newSettings;
    }

    @Override
    public boolean testConnection(TestConfluenceConnectionCommand command) {
        log.info("Testing Confluence connection to: {}", command.baseUrl());

        try {
            String baseUrl = command.baseUrl().endsWith("/")
                    ? command.baseUrl() + "rest/api/space"
                    : command.baseUrl() + "/rest/api/space";

            String credentials = Base64.getEncoder()
                    .encodeToString((command.username() + ":" + command.apiToken()).getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "?limit=1"))
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            log.info("Confluence connection test result: {} (HTTP {})", success ? "SUCCESS" : "FAILED", response.statusCode());
            return success;

        } catch (InterruptedException e) {
            log.warn("Confluence connection test interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Confluence connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private String encryptToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return "";
        }
        return encryptionService.encrypt(plainToken, TOKEN_METADATA);
    }
}
