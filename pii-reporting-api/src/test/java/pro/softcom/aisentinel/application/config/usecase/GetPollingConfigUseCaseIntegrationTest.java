package pro.softcom.aisentinel.application.config.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.config.port.out.ReadConfluenceConfigPort;
import pro.softcom.aisentinel.domain.config.PollingConfig;

@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class GetPollingConfigUseCaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Confluence configuration required by ConfluenceConfigAdapter / GetPollingConfigUseCase
        registry.add("confluence.cache.refresh-interval-ms", () -> 5_000L);
        registry.add("confluence.polling.interval-ms", () -> 2_000L);
    }

    private GetPollingConfigUseCase getPollingConfigUseCase;

    @Autowired
    private ReadConfluenceConfigPort confluenceConfigPort;


    @BeforeEach
    void setUp() {
        getPollingConfigUseCase = new GetPollingConfigUseCase(confluenceConfigPort);
    }

    @Test
    void Should_ReturnPollingConfig_When_ConfluencePropertiesDefined() {
        // Act
        PollingConfig config = getPollingConfigUseCase.getPollingConfig();

        // Assert
        assertThat(config).isNotNull();
        assertThat(config.backendRefreshIntervalMs()).isEqualTo(5_000L);
        assertThat(config.frontendPollingIntervalMs()).isEqualTo(2_000L);
    }
}
