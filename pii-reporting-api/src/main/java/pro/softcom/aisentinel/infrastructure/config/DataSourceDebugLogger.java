package pro.softcom.aisentinel.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

//FIXME: remove after usage
/**
 * Logs database connection configuration at application startup for debugging purposes.
 */
@Configuration
public class DataSourceDebugLogger {

    private static final Logger log = LoggerFactory.getLogger(DataSourceDebugLogger.class);

    @Value("${spring.datasource.username:NOT_SET}")
    private String username;

    @Value("${spring.datasource.password:NOT_SET}")
    private String password;

    @Value("${spring.datasource.url:NOT_SET}")
    private String url;

    @PostConstruct
    public void logDataSourceConfig() {
        log.info("=== DATABASE CONNECTION CONFIGURATION ===");
        log.info("spring.datasource.url: {}", url);
        log.info("spring.datasource.username: {}", username);
        log.info("spring.datasource.password: {}", password);
        log.info("=========================================");
    }

    private String maskPassword(String password) {
        if (password == null || password.equals("NOT_SET") || password.isEmpty()) {
            return password;
        }
        if (password.length() <= 4) {
            return "***";
        }
        return password.substring(0, 2) + "***" + password.substring(password.length() - 2);
    }
}
