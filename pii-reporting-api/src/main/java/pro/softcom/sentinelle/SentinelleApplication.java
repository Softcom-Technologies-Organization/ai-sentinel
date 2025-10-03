package pro.softcom.sentinelle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main Sentinelle application with Confluence integration.
 * Uses Java 24 with Virtual Threads and new features.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class SentinelleApplication {

    public static void main(String[] args) {
        // Enable Virtual Threads for Spring Boot
        System.setProperty("spring.threads.virtual.enabled", "true");
        
        SpringApplication.run(SentinelleApplication.class, args);
    }
}
