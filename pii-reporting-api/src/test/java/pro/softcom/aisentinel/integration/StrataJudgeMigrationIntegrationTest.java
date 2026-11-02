package pro.softcom.aisentinel.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StrataJudgeMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void Should_ChangeOnlyJudgeModel_When_MigrationAppliedTwice() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE pii_detection_config (
                        id INTEGER PRIMARY KEY, lm_studio_host TEXT, lm_studio_port INTEGER,
                        lm_studio_model TEXT, lm_studio_judge_model TEXT, ministral_enabled BOOLEAN,
                        updated_at TIMESTAMP, updated_by TEXT);
                    INSERT INTO pii_detection_config VALUES
                        (1, 'ministral', 1234, 'ministral-pii', 'gpt-oss-120b', false, NOW(), 'operator');
                    """);
            String migration = Files.readString(Path.of("../docker/dgnsi/upgrade-strata-judge.sql"));

            statement.execute(migration);
            statement.execute(migration);

            try (var row = statement.executeQuery("SELECT * FROM pii_detection_config")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("lm_studio_judge_model")).isEqualTo("qwen3.8-flash-next-iq3_s");
                assertThat(row.getString("lm_studio_host")).isEqualTo("ministral");
                assertThat(row.getInt("lm_studio_port")).isEqualTo(1234);
                assertThat(row.getString("lm_studio_model")).isEqualTo("ministral-pii");
                assertThat(row.getBoolean("ministral_enabled")).isFalse();
                assertThat(row.next()).isFalse();
            }
        }
    }
}
