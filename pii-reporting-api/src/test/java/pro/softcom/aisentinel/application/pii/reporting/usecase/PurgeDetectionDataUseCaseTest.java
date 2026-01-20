package pro.softcom.aisentinel.application.pii.reporting.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanCheckpointStore;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PurgeDetectionDataUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
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
    private ScanEventStore scanEventStore;

    @Autowired
    private ScanCheckpointStore scanCheckpointStore;

    @Autowired
    private DetectionEventRepository eventRepository;

    @Autowired
    private DetectionCheckpointRepository checkpointRepository;

    @BeforeEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        checkpointRepository.deleteAll();
        // recreate use case to ensure fresh instance if needed
        purgeDetectionDataUseCase = new PurgeDetectionDataUseCase(scanEventStore, scanCheckpointStore);
    }

    private PurgeDetectionDataUseCase purgeDetectionDataUseCase;

    @Test
    void Should_DeleteAllEventsAndCheckpoints_When_DataExists() {
        // Arrange
        var now = Instant.parse("2024-01-01T12:00:00Z");
        var scanId = "scan-purge-1";
        JsonNode payload = new ObjectMapper().createObjectNode();

        // seed one event
        eventRepository.save(ScanEventEntity.builder()
                                 .scanId(scanId)
                                 .eventSeq(1L)
                                 .spaceKey("SPACE-A")
                                 .eventType("PAGE_COMPLETE")
                                 .ts(now)
                                 .pageId("p1")
                                 .pageTitle("title")
                                 .attachmentName(null)
                                 .attachmentType(null)
                                 .payload(payload)
                                 .build());

        // seed one checkpoint
        checkpointRepository.save(ScanCheckpointEntity.builder()
                                      .scanId(scanId)
                                      .spaceKey("SPACE-A")
                                      .lastProcessedPageId("p1")
                                      .lastProcessedAttachmentName(null)
                                      .status("COMPLETED")
                                      .progressPercentage(100.0)
                                      .updatedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                                      .build());

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(checkpointRepository.count()).isEqualTo(1);

        // Act
        purgeDetectionDataUseCase.purgeAll();

        // Assert
        assertThat(eventRepository.count()).isZero();
        assertThat(checkpointRepository.count()).isZero();
    }

    @Test
    void Should_BeIdempotent_When_NoDataPresent() {
        // Arrange
        assertThat(eventRepository.count()).isZero();
        assertThat(checkpointRepository.count()).isZero();

        // Act
        purgeDetectionDataUseCase.purgeAll();
        purgeDetectionDataUseCase.purgeAll();

        // Assert
        assertThat(eventRepository.count()).isZero();
        assertThat(checkpointRepository.count()).isZero();
    }
}
