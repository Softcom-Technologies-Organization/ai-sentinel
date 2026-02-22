package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiTypeConfigJpaRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PII Type Config Consistency Integration Test")
class PiiTypeConfigConsistencyTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Ensure data.sql is executed
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private PiiTypeConfigJpaRepository repository;

    @Test
    @DisplayName("Should_LoadAllDbValues_When_LoadingFromDataSql")
    void Should_LoadAllDbValues_When_LoadingFromDataSql() {
        // Act
        List<PiiTypeConfigEntity> allConfigs = repository.findAll();

        // Assert
        assertThat(allConfigs)
                .as("Database should not be empty")
                .isNotEmpty();

        // Check that we have coverage for the critical GLiNER detector
        List<String> glinerTypes = allConfigs.stream()
                .filter(config -> "GLINER".equals(config.getDetector()))
                .map(PiiTypeConfigEntity::getPiiType)
                .toList();

        assertThat(glinerTypes)
                .as("Should contain basic types")
                .contains("PERSON_NAME", "EMAIL", "PHONE_NUMBER");
    }

    @Test
    @DisplayName("Should_HaveNonBlankPiiType_When_CheckingAllEntries")
    void Should_HaveNonBlankPiiType_When_CheckingAllEntries() {
        // Act
        List<PiiTypeConfigEntity> allConfigs = repository.findAll();

        // Assert - all entries have non-blank piiType
        assertThat(allConfigs)
                .as("All config entries should have a non-blank piiType")
                .allSatisfy(config ->
                        assertThat(config.getPiiType()).isNotBlank()
                );

        // Verify we have distinct types for each detector
        Set<String> distinctTypes = allConfigs.stream()
                .map(PiiTypeConfigEntity::getPiiType)
                .collect(Collectors.toSet());

        assertThat(distinctTypes)
                .as("Database should contain multiple distinct PII types")
                .hasSizeGreaterThan(1);
    }
}