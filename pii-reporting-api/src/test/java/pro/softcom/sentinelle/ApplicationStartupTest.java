package pro.softcom.sentinelle;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test that verifies the Spring Boot application context starts without errors.
 *
 * Business intent: Ensure the application can bootstrap under a controlled "test" profile
 * without requiring external services (Confluence, gRPC server), catching wiring/config issues early.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Assert the main Spring context is created
        assertNotNull(applicationContext, "ApplicationContext should have been initialized");
    }
}
