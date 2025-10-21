package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs Confluence configuration at application startup for debugging purposes.
 * This helps verify that environment variables are correctly loaded.
 */
@Component
public class ConfluenceConfigurationLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceConfigurationLogger.class);
    private final ConfluenceConfig confluenceConfig;

    public ConfluenceConfigurationLogger(ConfluenceConfig confluenceConfig) {
        this.confluenceConfig = confluenceConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=".repeat(80));
        log.info("Configuration Confluence chargée avec succès :");
        log.info("  - Base URL       : {}", confluenceConfig.baseUrl());
        log.info("  - Username       : {}", confluenceConfig.username());
        log.info("  - API Token      : {} (longueur: {})", 
            maskToken(confluenceConfig.apiToken()), 
            confluenceConfig.apiToken() != null ? confluenceConfig.apiToken().length() : 0);
        log.info("  - Connect Timeout: {} ms", confluenceConfig.connectTimeout());
        log.info("  - Read Timeout   : {} ms", confluenceConfig.readTimeout());
        log.info("  - Max Retries    : {}", confluenceConfig.maxRetries());
        log.info("  - Enable Proxy   : {}", confluenceConfig.enableProxy());
        
        if (confluenceConfig.enableProxy() && confluenceConfig.proxyHost() != null) {
            log.info("  - Proxy Host     : {}", confluenceConfig.proxyHost());
            log.info("  - Proxy Port     : {}", confluenceConfig.proxyPort());
            log.info("  - Proxy Username : {}", 
                confluenceConfig.proxyUsername() != null ? "***configuré***" : "non configuré");
        }
        
        log.info("=".repeat(80));
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
