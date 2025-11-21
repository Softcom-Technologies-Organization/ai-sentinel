package pro.softcom.sentinelle.application.pii.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
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
import pro.softcom.sentinelle.SentinelleApplication;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;

@Testcontainers
@SpringBootTest(classes = SentinelleApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PauseScanUseCaseTest {

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

    private PauseScanUseCase pauseScanUseCase;

    @Autowired
    private DetectionCheckpointRepository checkpointRepository;

    @Autowired
    private ScanCheckpointRepository scanCheckpointRepository;


    @BeforeEach
    void cleanDatabase() {
        pauseScanUseCase = new PauseScanUseCase(scanCheckpointRepository);
        checkpointRepository.deleteAll();
    }

    @Test
    void Should_PauseNonTerminalCheckpoints_When_ScanHasMixedStatuses() {
        // Arrange
        var scanId = "scan-100";
        var now = LocalDateTime.of(2024, 1, 1, 12, 0);

        // running checkpoint should be paused
        checkpointRepository.save(ScanCheckpointEntity.builder()
                                      .scanId(scanId)
                                      .spaceKey("SPACE-RUNNING")
                                      .lastProcessedPageId("p1")
                                      .lastProcessedAttachmentName("a1")
                                      .status(ScanStatus.RUNNING.name())
                                      .progressPercentage(10.0)
                                      .updatedAt(now)
                                      .build());

        // completed checkpoint should stay completed
        checkpointRepository.save(ScanCheckpointEntity.builder()
                                      .scanId(scanId)
                                      .spaceKey("SPACE-COMPLETED")
                                      .lastProcessedPageId("p2")
                                      .lastProcessedAttachmentName("a2")
                                      .status(ScanStatus.COMPLETED.name())
                                      .progressPercentage(100.0)
                                      .updatedAt(now)
                                      .build());

        // failed checkpoint should stay failed
        checkpointRepository.save(ScanCheckpointEntity.builder()
                                      .scanId(scanId)
                                      .spaceKey("SPACE-FAILED")
                                      .lastProcessedPageId("p3")
                                      .lastProcessedAttachmentName("a3")
                                      .status(ScanStatus.FAILED.name())
                                      .progressPercentage(50.0)
                                      .updatedAt(now)
                                      .build());

        // Act
        pauseScanUseCase.pauseScan(scanId);

        // Assert
        List<ScanCheckpointEntity> all =
            checkpointRepository.findByScanIdOrderBySpaceKey(scanId);

        assertThat(all).hasSize(3);

        SoftAssertions softly = new SoftAssertions();

        var running = all.stream()
            .filter(e -> "SPACE-RUNNING".equals(e.getSpaceKey()))
            .findFirst()
            .orElseThrow();
        softly.assertThat(running.getStatus()).isEqualTo(ScanStatus.PAUSED.name());

        var completed = all.stream()
            .filter(e -> "SPACE-COMPLETED".equals(e.getSpaceKey()))
            .findFirst()
            .orElseThrow();
        softly.assertThat(completed.getStatus()).isEqualTo(ScanStatus.COMPLETED.name());

        var failed = all.stream()
            .filter(e -> "SPACE-FAILED".equals(e.getSpaceKey()))
            .findFirst()
            .orElseThrow();
        softly.assertThat(failed.getStatus()).isEqualTo(ScanStatus.FAILED.name());

        softly.assertAll();
    }

    @Test
    void Should_DoNothing_When_ScanIdBlank() {
        // Arrange
        var before = checkpointRepository.count();

        // Act
        pauseScanUseCase.pauseScan(" ");

        // Assert
        var after = checkpointRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
