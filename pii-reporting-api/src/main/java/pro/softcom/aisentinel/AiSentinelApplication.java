package pro.softcom.aisentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main AI-Sentinel application with Confluence integration.
 * Uses Java 25 with Virtual Threads and new features.
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class AiSentinelApplication {

    public static void main(String[] args) {
        // Enable Virtual Threads for Spring Boot
        System.setProperty("spring.threads.virtual.enabled", "true");
        
        SpringApplication.run(AiSentinelApplication.class, args);
    }
}
