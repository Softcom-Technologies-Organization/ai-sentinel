package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

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
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code scan_detector_stats} UPSERT against a real PostgreSQL.
 *
 * <p>The accumulation is expressed as {@code ON CONFLICT DO UPDATE}, so neither the
 * summing nor the "keep the first failure reason" rule can be observed without a
 * database that actually resolves the conflict.
 */
@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ScanDetectorStatsUpsertIntegrationTest {

    private static final String SPACE_KEY = "SPACE";
    private static final String DETECTOR = "MINISTRAL";

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
    }

    @Autowired
    private ScanDetectorStatsJpaRepository repository;

    @Test
    void Should_SumEveryCounter_When_SameDetectorAccumulatedTwice() {
        String scanId = "scan-sum";

        repository.accumulate(scanId, SPACE_KEY,
            new ScanDetectorStatDelta(DETECTOR, 520L, 1_000L, 12, 1, 0, ""));
        repository.accumulate(scanId, SPACE_KEY,
            new ScanDetectorStatDelta(DETECTOR, 80L, 250L, 3, 2, 1, "endpoint unreachable"));

        ScanDetectorStatsEntity stats = onlyRowOf(scanId);
        assertThat(stats.getBusyMs()).isEqualTo(600L);
        assertThat(stats.getCharsProcessed()).isEqualTo(1_250L);
        assertThat(stats.getDetections()).isEqualTo(15);
        assertThat(stats.getDiscarded()).isEqualTo(3);
        assertThat(stats.getFailedRequests()).isEqualTo(1);
    }

    @Test
    void Should_KeepEarlierFailureReason_When_LaterRequestSucceeds() {
        String scanId = "scan-error";

        repository.accumulate(scanId, SPACE_KEY,
            new ScanDetectorStatDelta(DETECTOR, 10L, 20L, 0, 0, 1, "endpoint unreachable"));
        repository.accumulate(scanId, SPACE_KEY,
            new ScanDetectorStatDelta(DETECTOR, 30L, 40L, 5, 0, 0, ""));

        assertThat(onlyRowOf(scanId).getLastError()).isEqualTo("endpoint unreachable");
    }

    @Test
    void Should_LeaveErrorUnset_When_NoRequestEverFailed() {
        String scanId = "scan-clean";

        repository.accumulate(scanId, SPACE_KEY,
            new ScanDetectorStatDelta(DETECTOR, 10L, 20L, 1, 0, 0, ""));

        assertThat(onlyRowOf(scanId).getLastError()).isNull();
    }

    private ScanDetectorStatsEntity onlyRowOf(String scanId) {
        List<ScanDetectorStatsEntity> rows =
            repository.findById_ScanIdAndId_SpaceKeyOrderById_Detector(scanId, SPACE_KEY);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
