package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiTypeConfigJpaRepository;

import java.util.List;
import java.util.Map;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void Should_SeedNationalRulesDisabledAndKeepOperatorChoices_When_ReplayingSeed() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pii_type_config "
                + "WHERE detector = 'REGEX' AND (pii_type LIKE 'SSN\\_%' OR pii_type LIKE 'TAX\\_ID\\_%') "
                + "AND enabled", Integer.class)).isZero();
        jdbcTemplate.update("UPDATE pii_type_config SET enabled = true, threshold = 0.99 "
                + "WHERE pii_type = 'SSN_FR_NIR' AND detector = 'REGEX'");
        jdbcTemplate.update("UPDATE pii_type_config SET enabled = false, threshold = 0.98 "
                + "WHERE pii_type IN ('AVS_NUMBER', 'SOCIALNUM') AND detector = 'REGEX'");

        replaySeed();

        assertThat(regexRule("SSN_FR_NIR")).containsEntry("enabled", true)
                .containsEntry("threshold", 0.99);
        assertThat(regexRule("SOCIALNUM")).containsEntry("enabled", false)
                .containsEntry("threshold", 0.98);
        assertThat(regexRule("AVS_NUMBER")).containsEntry("enabled", false)
                .containsEntry("threshold", 0.98).containsEntry("category", "SSN");
    }

    private Map<String, Object> regexRule(String type) {
        return jdbcTemplate.queryForMap("SELECT enabled, threshold, category FROM pii_type_config "
                + "WHERE pii_type = ? AND detector = 'REGEX'", type);
    }

    private void replaySeed() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
            return null;
        });
    }

    @Test
    @DisplayName("Should_LoadAllDbValues_When_LoadingFromDataSql")
    void Should_LoadAllDbValues_When_LoadingFromDataSql() {
        // Act
        List<PiiTypeConfigEntity> allConfigs = repository.findAll();

        // Assert
        assertThat(allConfigs)
                .as("Database should not be empty")
                .isNotEmpty();

        // Check that we have coverage for the critical MINISTRAL detector
        List<String> ministralTypes = allConfigs.stream()
                .filter(config -> "MINISTRAL".equals(config.getDetector()))
                .map(PiiTypeConfigEntity::getPiiType)
                .toList();

        assertThat(ministralTypes)
                .as("Should contain basic types")
                .contains("FIRST_NAME", "EMAIL", "PHONE_NUMBER");
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
