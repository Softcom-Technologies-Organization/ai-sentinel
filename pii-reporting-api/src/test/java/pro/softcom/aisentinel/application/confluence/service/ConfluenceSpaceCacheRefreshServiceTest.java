package pro.softcom.aisentinel.application.confluence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.confluence.port.out.ConfluenceClient;
import pro.softcom.aisentinel.domain.confluence.ConfluenceSpace;
import pro.softcom.aisentinel.domain.confluence.DataOwners;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.ConfluenceSpaceJpaRepository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

/**
 * Integration test for ConfluenceSpaceCacheRefreshService.
 * Business goal: When Confluence returns spaces, they are persisted into the cache (DB).
 */
@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class)
@ActiveProfiles("test")
@Import(ConfluenceSpaceCacheRefreshServiceTest.TestBeans.class)
@TestInstance(Lifecycle.PER_CLASS)
class ConfluenceSpaceCacheRefreshServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Ensure Confluence cache properties are present to avoid NPE in service logging
        registry.add("confluence.cache.refresh-interval-ms", () -> 1000L);
        registry.add("confluence.cache.initial-delay-ms", () -> 0L);
        // Disable background scheduling during this IT to avoid race with Mockito stubbing
        registry.add("spring.task.scheduling.enabled", () -> "false");
        // Minimal required confluence core props are already provided by application-test.yml,
        // but they can be overridden here if needed.
    }

    @Autowired
    private ConfluenceSpaceCacheRefreshService refreshService;

    @Autowired
    private ConfluenceSpaceJpaRepository jpaRepository;

    @Autowired
    private ConfluenceClient confluenceClient; // provided by TestConfiguration as Mockito mock

    //FIXME: this test needs to be fixed
    @Disabled("Requires manual execution")
    @Test
    void Should_PersistSpaces_When_BackgroundRefreshRuns() {
        // Arrange
        var s1 = new ConfluenceSpace("id-ONE", "ONE", "One Space", "http://one", "desc one",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        var s2 = new ConfluenceSpace("id-TWO", "TWO", "Two Space", "http://two", "desc two",
            ConfluenceSpace.SpaceType.GLOBAL, ConfluenceSpace.SpaceStatus.CURRENT, new DataOwners.NotLoaded(), null);
        when(confluenceClient.getAllSpaces()).thenReturn(CompletableFuture.completedFuture(List.of(s1, s2)));

        // Act
        refreshService.saveNewConfluenceSpaces();

        // Assert
        List<ConfluenceSpaceEntity> all = jpaRepository.findAllByOrderByNameAsc();
        assertThat(all).hasSize(2);
        assertThat(all.stream().map(ConfluenceSpaceEntity::getSpaceKey).toList())
            .containsExactlyInAnyOrder("ONE", "TWO");
        assertThat(all.stream().map(ConfluenceSpaceEntity::getName).toList())
            .containsExactlyInAnyOrder("One Space", "Two Space");
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        ConfluenceClient confluenceClient() {
            return Mockito.mock(ConfluenceClient.class);
        }
    }
}
