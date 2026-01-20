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
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.PersonallyIdentifiableInformationType;
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
    @DisplayName("Should_MapAllDbValuesToEnum_When_LoadingFromDataSql")
    void Should_MapAllDbValuesToEnum_When_LoadingFromDataSql() {
        // Act
        // This will fail if any pii_type string in DB cannot be mapped to the Enum
        List<PiiTypeConfigEntity> allConfigs = repository.findAll();

        // Assert
        assertThat(allConfigs)
                .as("Database should not be empty")
                .isNotEmpty();

        // Check that we have coverage for the critical GLiNER detector
        List<PersonallyIdentifiableInformationType> glinerTypes = allConfigs.stream()
                .filter(config -> "GLINER".equals(config.getDetector()))
                .map(PiiTypeConfigEntity::getPiiType)
                .toList();

        assertThat(glinerTypes)
                .as("Should contain basic types")
                .contains(
                        PersonallyIdentifiableInformationType.PERSON_NAME,
                        PersonallyIdentifiableInformationType.EMAIL,
                        PersonallyIdentifiableInformationType.PHONE_NUMBER
                );
    }

    @Test
    @DisplayName("Should_CoverAllEnumValues_When_CheckingDatabase")
    void Should_CoverAllEnumValues_When_CheckingDatabase() {
        // This test ensures that every Enum value has at least one configuration in the DB
        // This prevents declaring an Enum value but forgetting to add it to data.sql

        // Arrange
        Set<PersonallyIdentifiableInformationType> allEnumValues = Set.of(PersonallyIdentifiableInformationType.values());
        
        // Act
        Set<PersonallyIdentifiableInformationType> dbValues = repository.findAll().stream()
                .map(PiiTypeConfigEntity::getPiiType)
                .collect(Collectors.toSet());

        // Assert
        // We filter out UNKNOWN
        Set<PersonallyIdentifiableInformationType> expectedValues = allEnumValues.stream()
                .filter(t -> t != PersonallyIdentifiableInformationType.UNKNOWN)
                .collect(Collectors.toSet());

        assertThat(dbValues)
                .as("Database should contain configuration for all known PII types")
                .containsAll(expectedValues);
    }
}