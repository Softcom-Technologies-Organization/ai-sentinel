package pro.softcom.sentinelle.application.pii.reporting.usecase;

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
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.application.pii.security.PiiAccessAuditService;
import pro.softcom.sentinelle.application.pii.security.ScanResultEncryptor;
import pro.softcom.sentinelle.application.pii.security.port.out.SavePiiAuditPort;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;
import pro.softcom.sentinelle.domain.pii.security.PiiAuditRecord;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.JpaScanResultQueryAdapter;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.ScanCheckpointPersistenceAdapter;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

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

        List<ScanResult> results = scanReportingUseCase.getLatestSpaceScanResultList();
        assertThat(results).hasSize(1);
        ScanResult result = results.getFirst();
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
}
