package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import org.assertj.core.api.SoftAssertions;
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
import pro.softcom.aisentinel.domain.pii.detection.ConcurrencyBenchStatus;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
    classes = AiSentinelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class PiiDetectionConfigPersistenceAdapterTest {

    private static final int CONFIG_ID = 1;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private PiiDetectionConfigPersistenceAdapter persistenceAdapter;

    @Autowired
    private PiiDetectionConfigJpaRepository jpaRepository;

    @Test
    void Should_CreateAndPersistDefaultConfig_When_ConfigDoesNotExist() {
        jpaRepository.deleteAll();

        PiiDetectionConfig config = persistenceAdapter.findConfig();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config).isNotNull();
        softly.assertThat(config.id()).isOne();
        softly.assertThat(config.presidioEnabled()).isTrue();
        softly.assertThat(config.regexEnabled()).isTrue();
        softly.assertThat(config.defaultThreshold())
            .isEqualByComparingTo(new BigDecimal("0.75"));
        softly.assertThat(config.postfilterEnabled()).isFalse();
        softly.assertThat(config.ministralConcurrency()).isEqualTo(1);
        softly.assertThat(config.ministralConcurrencyAuto()).isTrue();
        softly.assertThat(config.ministralConcurrencyTunedSignature()).isNull();
        softly.assertThat(config.updatedAt()).isNotNull();
        softly.assertThat(config.updatedBy()).isEqualTo("system");

        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElse(null);
        softly.assertThat(entity).isNotNull();
        assertThat(entity).isNotNull();
        softly.assertThat(entity.getPresidioEnabled()).isTrue();
        softly.assertThat(entity.getRegexEnabled()).isTrue();
        softly.assertThat(entity.getDefaultThreshold())
            .isEqualByComparingTo(new BigDecimal("0.75"));
        softly.assertThat(entity.getPostfilterEnabled()).isFalse();
        softly.assertThat(entity.getUpdatedAt()).isNotNull();
        softly.assertThat(entity.getUpdatedBy()).isEqualTo("system");

        softly.assertAll();
    }

    @Test
    void Should_UpdateAndReturnConfig_When_ConfigAlreadyExists() {
        jpaRepository.deleteAll();

        PiiDetectionConfig existingConfig = persistenceAdapter.findConfig();

        BigDecimal newThreshold = existingConfig.defaultThreshold()
            .add(new BigDecimal("0.10"));
        LocalDateTime updateTime = LocalDateTime.now();

        PiiDetectionConfig updatedConfig = new PiiDetectionConfig(
            existingConfig.id(),
            false,
            existingConfig.regexEnabled(),
            false,
            1024, 128,
            newThreshold,
            false, "localhost", 1234, null, 1, true, null,
            updateTime,
            "integration-test"
        );

        persistenceAdapter.updateConfig(updatedConfig);

        PiiDetectionConfig reloadedConfig = persistenceAdapter.findConfig();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reloadedConfig.id()).isOne();
        softly.assertThat(reloadedConfig.presidioEnabled()).isFalse();
        softly.assertThat(reloadedConfig.regexEnabled())
            .isEqualTo(existingConfig.regexEnabled());
        softly.assertThat(reloadedConfig.defaultThreshold())
            .isEqualByComparingTo(newThreshold);
        softly.assertThat(reloadedConfig.updatedAt()).isNotNull();
        softly.assertThat(reloadedConfig.updatedBy()).isEqualTo("integration-test");

        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElse(null);
        softly.assertThat(entity).isNotNull();
        assertThat(entity).isNotNull();
        softly.assertThat(entity.getPresidioEnabled()).isFalse();
        softly.assertThat(entity.getDefaultThreshold())
            .isEqualByComparingTo(newThreshold);
        softly.assertThat(entity.getUpdatedBy()).isEqualTo("integration-test");

        softly.assertAll();
    }

    @Test
    void Should_PersistAndRetrievePostfilterEnabled_When_FlagIsEnabled() {
        jpaRepository.deleteAll();

        PiiDetectionConfig enabledConfig = new PiiDetectionConfig(
            CONFIG_ID,
            true,
            true,
            true,
            1024, 128, new BigDecimal("0.75"),
            true, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(),
            "postfilter-enabler"
        );

        persistenceAdapter.updateConfig(enabledConfig);

        PiiDetectionConfig reloaded = persistenceAdapter.findConfig();
        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reloaded.postfilterEnabled()).isTrue();
        softly.assertThat(entity.getPostfilterEnabled()).isTrue();
        softly.assertAll();
    }

    @Test
    void Should_RoundTripMinistralFields_When_Persisted() {
        jpaRepository.deleteAll();

        PiiDetectionConfig config = new PiiDetectionConfig(
            CONFIG_ID,
            true, false, true, 2048, 256, new BigDecimal("0.75"),
            false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(),
            "ministral-enabler"
        );

        persistenceAdapter.updateConfig(config);

        PiiDetectionConfig reloaded = persistenceAdapter.findConfig();
        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reloaded.ministralEnabled()).isTrue();
        softly.assertThat(reloaded.ministralChunkSize()).isEqualTo(2048);
        softly.assertThat(reloaded.ministralOverlap()).isEqualTo(256);
        softly.assertThat(entity.getMinistralEnabled()).isTrue();
        softly.assertThat(entity.getMinistralChunkSize()).isEqualTo(2048);
        softly.assertThat(entity.getMinistralOverlap()).isEqualTo(256);
        softly.assertAll();
    }

    @Test
    void Should_RoundTripMinistralConcurrencyFields_When_Persisted() {
        jpaRepository.deleteAll();

        PiiDetectionConfig config = new PiiDetectionConfig(
            CONFIG_ID,
            true, false, true, 2048, 256, new BigDecimal("0.75"),
            false, "localhost", 1234, null, 4, false, "localhost:1234|ministral",
            LocalDateTime.now(),
            "concurrency-tuner"
        );

        persistenceAdapter.updateConfig(config);

        PiiDetectionConfig reloaded = persistenceAdapter.findConfig();
        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reloaded.ministralConcurrency()).isEqualTo(4);
        softly.assertThat(reloaded.ministralConcurrencyAuto()).isFalse();
        softly.assertThat(reloaded.ministralConcurrencyTunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertThat(entity.getMinistralConcurrency()).isEqualTo(4);
        softly.assertThat(entity.getMinistralConcurrencyAuto()).isFalse();
        softly.assertThat(entity.getMinistralConcurrencyTunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertAll();
    }

    @Test
    void Should_FlagPendingBenchmarkRequest_When_RequestBenchmarkCalled() {
        jpaRepository.deleteAll();
        persistenceAdapter.findConfig();

        persistenceAdapter.requestBenchmark(8);

        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();
        ConcurrencyBenchStatus status = persistenceAdapter.findBenchStatus();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity.getConcurrencyBenchRequested()).isTrue();
        softly.assertThat(entity.getConcurrencyBenchStatus()).isEqualTo("PENDING");
        softly.assertThat(entity.getConcurrencyBenchProgress()).isZero();
        softly.assertThat(entity.getConcurrencyBenchMessage()).isNull();
        softly.assertThat(entity.getConcurrencyBenchMaxConcurrency()).isEqualTo(8);
        softly.assertThat(status.status()).isEqualTo("PENDING");
        softly.assertThat(status.progress()).isZero();
        softly.assertThat(status.message()).isNull();
        softly.assertThat(status.maxConcurrency()).isEqualTo(8);
        softly.assertAll();
    }

    @Test
    void Should_WithdrawRequest_When_CancellingBeforeDetectorClaimedIt() {
        jpaRepository.deleteAll();
        persistenceAdapter.findConfig();
        persistenceAdapter.requestBenchmark(6);

        persistenceAdapter.cancelBenchmark();

        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity.getConcurrencyBenchRequested()).isFalse();
        softly.assertThat(entity.getConcurrencyBenchStatus()).isEqualTo("CANCELLED");
        softly.assertThat(entity.getConcurrencyBenchMessage()).isNotBlank();
        softly.assertAll();
    }

    @Test
    void Should_AskDetectorToStop_When_CancellingRunningBenchmark() {
        jpaRepository.deleteAll();
        persistenceAdapter.findConfig();
        PiiDetectionConfigEntity running = jpaRepository.findById(CONFIG_ID).orElseThrow();
        running.setConcurrencyBenchRequested(false);
        running.setConcurrencyBenchStatus("RUNNING");
        running.setConcurrencyBenchProgress(30);
        jpaRepository.save(running);

        persistenceAdapter.cancelBenchmark();

        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity.getConcurrencyBenchStatus()).isEqualTo("CANCEL_REQUESTED");
        softly.assertThat(entity.getConcurrencyBenchProgress()).isEqualTo(30);
        softly.assertAll();
    }

    @Test
    void Should_PreserveBenchState_When_ConfigIsUpdated() {
        jpaRepository.deleteAll();
        PiiDetectionConfig existingConfig = persistenceAdapter.findConfig();

        // Simulate a benchmark reported as running by the detector service.
        PiiDetectionConfigEntity entity = jpaRepository.findById(CONFIG_ID).orElseThrow();
        entity.setConcurrencyBenchRequested(true);
        entity.setConcurrencyBenchStatus("RUNNING");
        entity.setConcurrencyBenchProgress(42);
        entity.setConcurrencyBenchMessage("probing 4 workers");
        entity.setConcurrencyBenchMaxConcurrency(12);
        jpaRepository.save(entity);

        persistenceAdapter.updateConfig(new PiiDetectionConfig(
            existingConfig.id(),
            false, true, false, 1024, 128, new BigDecimal("0.80"),
            false, "localhost", 1234, null, 1, true, null,
            LocalDateTime.now(),
            "bench-preserver"
        ));

        PiiDetectionConfigEntity reloaded = jpaRepository.findById(CONFIG_ID).orElseThrow();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(reloaded.getPresidioEnabled()).isFalse();
        softly.assertThat(reloaded.getConcurrencyBenchRequested()).isTrue();
        softly.assertThat(reloaded.getConcurrencyBenchStatus()).isEqualTo("RUNNING");
        softly.assertThat(reloaded.getConcurrencyBenchProgress()).isEqualTo(42);
        softly.assertThat(reloaded.getConcurrencyBenchMessage()).isEqualTo("probing 4 workers");
        softly.assertThat(reloaded.getConcurrencyBenchMaxConcurrency()).isEqualTo(12);
        softly.assertAll();
    }

    @Test
    void Should_ExposeConcurrencyValuesInBenchStatus_When_ConfigTuned() {
        jpaRepository.deleteAll();

        persistenceAdapter.updateConfig(new PiiDetectionConfig(
            CONFIG_ID,
            true, true, true, 2048, 256, new BigDecimal("0.75"),
            false, "localhost", 1234, null, 4, true, "localhost:1234|ministral",
            LocalDateTime.now(),
            "concurrency-tuner"
        ));

        ConcurrencyBenchStatus status = persistenceAdapter.findBenchStatus();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(status.status()).isEqualTo("IDLE");
        softly.assertThat(status.progress()).isZero();
        softly.assertThat(status.message()).isNull();
        softly.assertThat(status.concurrency()).isEqualTo(4);
        softly.assertThat(status.tunedSignature()).isEqualTo("localhost:1234|ministral");
        softly.assertAll();
    }
}
