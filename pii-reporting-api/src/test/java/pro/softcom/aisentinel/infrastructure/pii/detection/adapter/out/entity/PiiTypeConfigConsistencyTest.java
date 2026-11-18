package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
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
    void Should_AddNullableJudgeColumns_When_MigratingLegacyConfiguration() {
        jdbcTemplate.update("UPDATE pii_detection_config SET lm_studio_judge_model = 'legacy-model' WHERE id = 1");
        jdbcTemplate.execute("ALTER TABLE pii_detection_config DROP COLUMN judge_provider, "
                + "DROP COLUMN judge_host, DROP COLUMN judge_port");
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            var migration = new FileSystemResource("init-scripts/022-judge-provider.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            return null;
        });

        assertThat(jdbcTemplate.queryForMap("SELECT judge_provider, judge_host, judge_port, lm_studio_judge_model "
                + "FROM pii_detection_config WHERE id = 1"))
                .containsEntry("judge_provider", null).containsEntry("judge_host", null)
                .containsEntry("judge_port", null).containsEntry("lm_studio_judge_model", "legacy-model");
    }

    @Test
    void Should_PreserveJudgeConfiguration_When_ReplayingSeed() {
        jdbcTemplate.update("UPDATE pii_detection_config SET judge_provider = 'OMLX', "
                + "judge_host = 'omlx', judge_port = 8000, lm_studio_judge_model = 'manual-model' WHERE id = 1");

        replaySeed();

        assertThat(jdbcTemplate.queryForMap("SELECT judge_provider, judge_host, judge_port, lm_studio_judge_model "
                + "FROM pii_detection_config WHERE id = 1"))
                .containsEntry("judge_provider", "OMLX").containsEntry("judge_host", "omlx")
                .containsEntry("judge_port", 8000).containsEntry("lm_studio_judge_model", "manual-model");
    }

    @Test
    void Should_PreserveTrioChoices_When_ReplayingSeed() {
        jdbcTemplate.update("UPDATE pii_type_config SET enabled = false, threshold = 0.67 "
                + "WHERE pii_type = 'PASSWORD' AND detector = 'ETTIN'");
        jdbcTemplate.update("UPDATE pii_detection_config SET trio_enabled = true, "
                + "bards_enabled = false, cbx_enabled = true, ettin_enabled = false WHERE id = 1");

        replaySeed();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pii_type_config "
                + "WHERE detector IN ('BARDS', 'CBX', 'ETTIN')", Integer.class)).isEqualTo(104);
        assertThat(jdbcTemplate.queryForMap("SELECT enabled, threshold FROM pii_type_config "
                + "WHERE pii_type = 'PASSWORD' AND detector = 'ETTIN'"))
                .containsEntry("enabled", false).containsEntry("threshold", 0.67);
        assertThat(jdbcTemplate.queryForMap("SELECT trio_enabled, bards_enabled, cbx_enabled, ettin_enabled "
                + "FROM pii_detection_config WHERE id = 1"))
                .containsEntry("trio_enabled", true).containsEntry("bards_enabled", false)
                .containsEntry("cbx_enabled", true).containsEntry("ettin_enabled", false);
    }

    @Test
    void Should_RemoveRetiredRulesAndKeepOperatorChoices_When_ReplayingSeed() {
        jdbcTemplate.update("INSERT INTO pii_type_config "
                + "(pii_type, detector, enabled, threshold, category, created_at, updated_at, updated_by) "
                + "SELECT retired.pii_type, retired.detector, true, 0.99, 'SSN', "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'operator' FROM (VALUES "
                + "('SSN_FR_NIR', 'REGEX'), ('TAX_ID_CH_UID', 'REGEX'), "
                + "('SSN_FR_NIR', 'PRESIDIO'), ('CUSTOM_REFERENCE', 'REGEX')) AS retired(pii_type, detector)");
        jdbcTemplate.update("UPDATE pii_type_config SET enabled = false, threshold = 0.98 "
                + "WHERE pii_type IN ('AVS_NUMBER', 'SOCIALNUM') AND detector = 'REGEX'");

        jdbcTemplate.update("UPDATE pii_detection_config "
                + "SET judge_enabled_categories = '[\"SSN\",\"TAX_ID\",\"IDENTITY\"]'::jsonb");

        replaySeed();
        replaySeed();

        assertThat(jdbcTemplate.queryForList("SELECT pii_type FROM pii_type_config "
                + "WHERE detector = 'REGEX'", String.class))
                .containsExactlyInAnyOrder("AVS_NUMBER", "SOCIALNUM", "API_KEY", "CUSTOM_REFERENCE");
        assertThat(jdbcTemplate.queryForObject("SELECT enabled FROM pii_type_config "
                + "WHERE detector = 'PRESIDIO' AND pii_type = 'SSN_FR_NIR'", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT judge_enabled_categories::text "
                + "FROM pii_detection_config WHERE id = 1", String.class)).isEqualTo("[\"SSN\", \"IDENTITY\"]");
        assertThat(regexRule("SOCIALNUM")).containsEntry("enabled", false)
                .containsEntry("threshold", 0.98);
        assertThat(regexRule("AVS_NUMBER")).containsEntry("enabled", false)
                .containsEntry("threshold", 0.98).containsEntry("category", "SSN");
    }

    @Test
    void Should_KeepJudgeCategory_When_CustomRuleStillUsesIt() {
        jdbcTemplate.update("UPDATE pii_type_config SET category = 'Tax ID' "
                + "WHERE pii_type = 'API_KEY' AND detector = 'REGEX'");
        jdbcTemplate.update("UPDATE pii_detection_config SET judge_enabled_categories = '[\"TAX_ID\"]'::jsonb");

        replaySeed();

        assertThat(jdbcTemplate.queryForObject("SELECT judge_enabled_categories::text "
                + "FROM pii_detection_config WHERE id = 1", String.class)).isEqualTo("[\"TAX_ID\"]");
        jdbcTemplate.update("UPDATE pii_type_config SET category = 'IT_CREDENTIALS' "
                + "WHERE pii_type = 'API_KEY' AND detector = 'REGEX'");
        replaySeed();
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
