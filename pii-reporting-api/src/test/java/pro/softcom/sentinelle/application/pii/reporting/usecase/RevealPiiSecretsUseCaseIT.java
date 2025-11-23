package pro.softcom.sentinelle.application.pii.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ReadPiiConfigPort;
import pro.softcom.sentinelle.application.pii.security.PiiAccessAuditService;
import pro.softcom.sentinelle.application.pii.security.ScanResultEncryptor;
import pro.softcom.sentinelle.domain.pii.reporting.PageSecretsResponse;
import pro.softcom.sentinelle.domain.pii.reporting.PiiEntity;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.domain.pii.security.EncryptionMetadata;
import pro.softcom.sentinelle.domain.pii.security.EncryptionService;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.JpaScanResultQueryAdapter;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaScanResultQueryAdapter.class,
    RevealPiiSecretsUseCase.class,
    RevealPiiSecretsUseCaseIT.TestConfig.class
})
class RevealPiiSecretsUseCaseIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        // Ensure the PostgreSQL Testcontainer is started before Spring tries to
        // initialize the datasource. While Testcontainers can start lazily on
        // first usage, the Spring Boot test context may attempt to connect
        // eagerly using the injected JDBC URL, which would fail if the
        // container is not yet running.
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
    private ScanResultQuery scanResultQuery;

    @Autowired
    private RevealPiiSecretsUseCase revealPiiSecretsUseCase;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("testReadPiiConfigPort")
    private ReadPiiConfigPort readPiiConfigPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper testObjectMapper() {
            return new ObjectMapper();
        }

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
        PiiAccessAuditService testPiiAccessAuditService() {
            return new PiiAccessAuditService(new pro.softcom.sentinelle.application.pii.security.port.out.SavePiiAuditPort() {
                @Override
                public void save(pro.softcom.sentinelle.domain.pii.security.PiiAuditRecord auditRecord) {
                    // no-op for integration test
                }

                @Override
                public int deleteExpiredRecords(java.time.Instant expirationDate) {
                    return 0;
                }
            }, 365);
        }

        @Bean
        @org.springframework.beans.factory.annotation.Qualifier("testReadPiiConfigPort")
        ReadPiiConfigPort testReadPiiConfigPort() {
            return new TestReadPiiConfigPort();
        }
    }

    static class TestReadPiiConfigPort implements ReadPiiConfigPort {

        private boolean allow = true;

        @Override
        public boolean isAllowSecretReveal() {
            return allow;
        }

        void setAllow(boolean allow) {
            this.allow = allow;
        }
    }

    @BeforeEach
    void cleanDb() {
        detectionEventRepository.deleteAll();
    }

    @Test
    void Should_ReturnPageSecretsResponseWithRevealedSecrets_When_SecretsExistAndRevealAllowed() {
        String scanId = "scan-reveal-1";
        String pageId = "page-1";

        ((TestReadPiiConfigPort) readPiiConfigPort).setAllow(true);

        PiiEntity entity = new PiiEntity(0, 5, "EMAIL", "Email", 0.99,
            "secret@example.com", "context", "masked");
        ScanResult scanResult = ScanResult.builder()
            .scanId(scanId)
            .spaceKey("SPACE-1")
            .eventType("item")
            .pageId(pageId)
            .pageTitle("Page 1")
            .detectedEntities(List.of(entity))
            .build();

        ObjectNode payload = objectMapper.valueToTree(scanResult);

        ScanEventEntity event = ScanEventEntity.builder()
            .scanId(scanId)
            .eventSeq(1L)
            .spaceKey("SPACE-1")
            .eventType("item")
            .ts(Instant.parse("2024-01-01T10:00:00Z"))
            .pageId(pageId)
            .pageTitle("Page 1")
            .payload(payload)
            .build();

        detectionEventRepository.save(event);

        Optional<PageSecretsResponse> response = revealPiiSecretsUseCase.revealPageSecrets(scanId, pageId);

        assertThat(response).isPresent();
        PageSecretsResponse pageSecrets = response.orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(pageSecrets.scanId()).isEqualTo(scanId);
        softly.assertThat(pageSecrets.pageId()).isEqualTo(pageId);
        softly.assertThat(pageSecrets.secrets()).hasSize(1);
        softly.assertThat(pageSecrets.secrets().getFirst().sensitiveValue()).isEqualTo("secret@example.com");
        softly.assertAll();
    }

    @Test
    void Should_ReturnEmptyOptional_When_NoResultsForPage() {
        String scanId = "scan-reveal-2";
        String pageId = "missing-page";

        ((TestReadPiiConfigPort) readPiiConfigPort).setAllow(true);

        Optional<PageSecretsResponse> response = revealPiiSecretsUseCase.revealPageSecrets(scanId, pageId);

        assertThat(response).isEmpty();
    }

    @Test
    void Should_ThrowSecurityException_When_RevealNotAllowedByConfig() {
        String scanId = "scan-reveal-3";
        String pageId = "page-1";

        ((TestReadPiiConfigPort) readPiiConfigPort).setAllow(false);

        assertThatThrownBy(() -> revealPiiSecretsUseCase.revealPageSecrets(scanId, pageId))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Secret revelation is not allowed by configuration");
    }
}
