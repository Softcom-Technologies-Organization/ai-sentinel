package pro.softcom.aisentinel.application.pii.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.aisentinel.application.pii.security.PiiAccessAuditService;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.domain.pii.security.EncryptionMetadata;
import pro.softcom.aisentinel.domain.pii.security.EncryptionService;
import pro.softcom.aisentinel.domain.pii.security.PiiAuditRecord;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.JpaScanResultQueryAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.ScanCheckpointPersistenceAdapter;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaScanResultQueryAdapter.class,
    ScanCheckpointPersistenceAdapter.class,
    ScanReportingUseCaseIT.TestEncryptionConfig.class
})
class ScanReportingUseCaseIT {

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
    }

    @Autowired
    private DetectionEventRepository detectionEventRepository;

    @Autowired
    private DetectionCheckpointRepository detectionCheckpointRepository;

    @Autowired
    private ScanResultQuery scanResultQuery;

    @Autowired
    private ScanCheckpointRepository scanCheckpointRepository;

    private ScanReportingUseCase scanReportingUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class TestEncryptionConfig {

        @Bean
        EncryptionService testEncryptionService() {
            return new EncryptionService() {
                @Override
                public String encrypt(String plaintext, EncryptionMetadata metadata) {
                    return plaintext;
                }

                @Override
                public String decrypt(String ciphertext, EncryptionMetadata metadata) {
                    return ciphertext;
                }

                @Override
                public boolean isEncrypted(String value) {
                    return false;
                }
            };
        }

        @Bean
        ScanResultEncryptor testScanResultEncryptor(EncryptionService testEncryptionService) {
            return new ScanResultEncryptor(testEncryptionService);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PiiAccessAuditService testPiiAccessAuditService() {
            SavePiiAuditPort savePiiAuditPort = new SavePiiAuditPort() {
                @Override
                public void save(PiiAuditRecord auditRecord) {
                    // no-op pour les tests d'integration
                }

                @Override
                public int deleteExpiredRecords(java.time.Instant expirationDate) {
                    return 0;
                }
            };

            return new PiiAccessAuditService(savePiiAuditPort, 365);
        }
    }

    @BeforeEach
    void cleanDb() {
        scanReportingUseCase = new ScanReportingUseCase(scanResultQuery, scanCheckpointRepository);
        detectionCheckpointRepository.deleteAll();
        detectionEventRepository.deleteAll();
    }

    @Test
    void Should_ReturnLatestScanMetaAndResults_When_EventsPresent() {
        String scanId = "scan-it-1";
        Instant now = Instant.parse("2024-01-01T10:00:00Z");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("scanId", scanId);
        payload.put("spaceKey", "SPACE-A");
        payload.put("eventType", "item");

        ScanEventEntity event = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(1L)
            .spaceKey("SPACE-A")
            .eventType("item")
            .ts(now)
            .pageId("page-1")
            .pageTitle("Page 1")
            .payload(payload)
            .build();
        detectionEventRepository.save(event);

        var latestScan = scanReportingUseCase.getLatestScan();

        assertThat(latestScan).isPresent();
        LastScanMeta meta = latestScan.orElseThrow();
        assertThat(meta.scanId()).isEqualTo(scanId);
        assertThat(meta.spacesCount()).isEqualTo(1);

        List<ConfluenceContentScanResult> results = scanReportingUseCase.getLatestSpaceScanResultList();
        assertThat(results).hasSize(1);
        ConfluenceContentScanResult result = results.getFirst();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.scanId()).isEqualTo(scanId);
        softly.assertThat(result.spaceKey()).isEqualTo("SPACE-A");
        softly.assertThat(result.eventType()).isEqualTo("item");
        softly.assertAll();
    }

    @Test
    void Should_ReturnSpaceStatesWithProgressAndStatus_When_CheckpointsAndEventsPresent() {
        String scanId = "scan-it-2";
        Instant now = Instant.parse("2024-02-01T10:00:00Z");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("scanId", scanId);
        payload.put("spaceKey", "SPACE-1");
        payload.put("eventType", "item");

        ScanEventEntity event = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(1L)
            .spaceKey("SPACE-1")
            .eventType("item")
            .ts(now)
            .pageId("page-1")
            .pageTitle("Page 1")
            .payload(payload)
            .build();
        detectionEventRepository.save(event);

        ScanCheckpoint checkpoint = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-1")
            .scanStatus(ScanStatus.RUNNING)
            .updatedAt(LocalDateTime.of(2024, 2, 1, 10, 0))
            .progressPercentage(25.0)
            .build();
        scanCheckpointRepository.save(checkpoint);

        var states = scanReportingUseCase.getLatestSpaceScanStateList(scanId);

        assertThat(states).hasSize(1);
        var state = states.getFirst();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(state.spaceKey()).isEqualTo("SPACE-1");
        softly.assertThat(state.status()).isEqualTo("RUNNING");
        softly.assertThat(state.progressPercentage()).isEqualTo(25.0);
        softly.assertAll();
    }

    @Test
    void Should_ReturnScanReportingSummaryWithAuthoritativeProgress_When_CheckpointsAndEventsPresent() {
        String scanId = "scan-it-3";
        Instant event1Ts = Instant.parse("2024-03-01T10:00:00Z");
        Instant event2Ts = Instant.parse("2024-03-01T10:30:00Z");

        // Arrange - Create events for SPACE-A (2 pages, 1 attachment)
        ObjectNode payload1 = objectMapper.createObjectNode();
        payload1.put("scanId", scanId);
        payload1.put("spaceKey", "SPACE-A");
        payload1.put("eventType", "item");

        ScanEventEntity event1 = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(1L)
            .spaceKey("SPACE-A")
            .eventType("pageComplete")
            .ts(event1Ts)
            .pageId("page-1")
            .pageTitle("Page 1")
            .payload(payload1)
            .build();
        detectionEventRepository.save(event1);

        ScanEventEntity event2 = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(2L)
            .spaceKey("SPACE-A")
            .eventType("pageComplete")
            .ts(event1Ts)
            .pageId("page-2")
            .pageTitle("Page 2")
            .payload(payload1)
            .build();
        detectionEventRepository.save(event2);

        ObjectNode attachmentPayload = objectMapper.createObjectNode();
        attachmentPayload.put("scanId", scanId);
        attachmentPayload.put("spaceKey", "SPACE-A");
        attachmentPayload.put("eventType", "attachment");

        ScanEventEntity event3 = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(3L)
            .spaceKey("SPACE-A")
            .eventType("attachmentItem")
            .ts(event1Ts)
            .pageId("page-1")
            .attachmentName("Attachment 1")
            .attachmentType("application/pdf")
            .payload(attachmentPayload)
            .build();
        detectionEventRepository.save(event3);

        // Create checkpoint for SPACE-A with COMPLETED status and 100% progress
        ScanCheckpoint checkpointA = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-A")
            .scanStatus(ScanStatus.COMPLETED)
            .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 30))
            .progressPercentage(100.0)
            .build();
        scanCheckpointRepository.save(checkpointA);

        // Create events for SPACE-B (1 page)
        ObjectNode payload2 = objectMapper.createObjectNode();
        payload2.put("scanId", scanId);
        payload2.put("spaceKey", "SPACE-B");
        payload2.put("eventType", "item");

        ScanEventEntity event4 = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(4L)
            .spaceKey("SPACE-B")
            .eventType("pageComplete")
            .ts(event2Ts)
            .pageId("page-b1")
            .pageTitle("Page B1")
            .payload(payload2)
            .build();
        detectionEventRepository.save(event4);

        // Create checkpoint for SPACE-B with RUNNING status and 33% progress
        ScanCheckpoint checkpointB = ScanCheckpoint.builder()
            .scanId(scanId)
            .spaceKey("SPACE-B")
            .scanStatus(ScanStatus.RUNNING)
            .updatedAt(LocalDateTime.of(2024, 3, 1, 10, 30))
            .progressPercentage(33.0)
            .build();
        scanCheckpointRepository.save(checkpointB);

        // Act
        var summary = scanReportingUseCase.getScanReportingSummary(scanId);

        // Assert
        assertThat(summary).isPresent();
        var scanSummary = summary.orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        
        // Verify nbOfDetectedPIIBySeverity metadata
        softly.assertThat(scanSummary.scanId()).isEqualTo(scanId);
        softly.assertThat(scanSummary.spacesCount()).isEqualTo(2);
        softly.assertThat(scanSummary.lastUpdated()).isEqualTo(event2Ts);
        
        // Verify spaces list
        softly.assertThat(scanSummary.spaces()).hasSize(2);
        
        // Find SPACE-A in nbOfDetectedPIIBySeverity
        var spaceA = scanSummary.spaces().stream()
            .filter(s -> "SPACE-A".equals(s.spaceKey()))
            .findFirst()
            .orElseThrow();
        
        // CRITICAL: Progress must come from checkpoint (100.0), NOT from events
        softly.assertThat(spaceA.progressPercentage())
            .as("SPACE-A progress must be 100.0 from checkpoint, not from events")
            .isEqualTo(100.0);
        
        softly.assertThat(spaceA.status()).isEqualTo("COMPLETED");
        
        // Counters come from events
        softly.assertThat(spaceA.pagesDone())
            .as("SPACE-A pages count from events")
            .isEqualTo(2);
        softly.assertThat(spaceA.attachmentsDone())
            .as("SPACE-A attachments count from events")
            .isEqualTo(1);
        
        // Find SPACE-B in nbOfDetectedPIIBySeverity
        var spaceB = scanSummary.spaces().stream()
            .filter(s -> "SPACE-B".equals(s.spaceKey()))
            .findFirst()
            .orElseThrow();
        
        // Progress must come from checkpoint (33.0)
        softly.assertThat(spaceB.progressPercentage())
            .as("SPACE-B progress must be 33.0 from checkpoint")
            .isEqualTo(33.0);
        
        softly.assertThat(spaceB.status()).isEqualTo("RUNNING");
        
        // Counters come from events
        softly.assertThat(spaceB.pagesDone())
            .as("SPACE-B pages count from events")
            .isEqualTo(1);
        softly.assertThat(spaceB.attachmentsDone())
            .as("SPACE-B attachments count from events")
            .isEqualTo(0);
        
        softly.assertAll();
    }
}
