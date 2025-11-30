package pro.softcom.aisentinel.application.pii.detection;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.AiSentinelApplication;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort;
import pro.softcom.aisentinel.application.pii.detection.port.in.ManagePiiDetectionConfigPort.UpdatePiiDetectionConfigCommand;
import pro.softcom.aisentinel.domain.pii.detection.PiiDetectionConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiDetectionConfigJpaRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for PII detection configuration management.
 * Tests the complete flow from use case through persistence adapter to database.
 */
@Testcontainers
@SpringBootTest(classes = AiSentinelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class ManagePiiDetectionConfigUseCaseTest {

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
    private ManagePiiDetectionConfigPort managePiiDetectionConfigPort;

    @Autowired
    private PiiDetectionConfigJpaRepository jpaRepository;

    @Test
    void Should_CreateDefaultConfig_When_NoConfigExistsInDatabase() {
        // Act
        PiiDetectionConfig config = managePiiDetectionConfigPort.getConfig();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(config).isNotNull();
        softly.assertThat(config.getId()).isEqualTo(1);
        softly.assertThat(config.isGlinerEnabled()).isTrue();
        softly.assertThat(config.isPresidioEnabled()).isTrue();
        softly.assertThat(config.isRegexEnabled()).isTrue();
        softly.assertThat(config.getDefaultThreshold()).isEqualByComparingTo(new BigDecimal("0.80"));
        softly.assertThat(config.getUpdatedBy()).isEqualTo("system");
        softly.assertAll();
    }

    @Test
    void Should_PersistAndRetrieveConfig_When_UpdatingConfiguration() {
        // Arrange
        UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
            true, false, true, new BigDecimal("0.85"), "integrationtest"
        );

        // Act
        PiiDetectionConfig updated = managePiiDetectionConfigPort.updateConfig(command);
        PiiDetectionConfig retrieved = managePiiDetectionConfigPort.getConfig();

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(updated.getId()).isEqualTo(1);
        softly.assertThat(updated.isGlinerEnabled()).isTrue();
        softly.assertThat(updated.isPresidioEnabled()).isFalse();
        softly.assertThat(updated.isRegexEnabled()).isTrue();
        softly.assertThat(updated.getDefaultThreshold()).isEqualByComparingTo(new BigDecimal("0.85"));
        softly.assertThat(updated.getUpdatedBy()).isEqualTo("integrationtest");
        
        softly.assertThat(retrieved).isEqualTo(updated);
        softly.assertAll();
    }

    @Test
    void Should_UpdateExistingConfig_When_ConfigAlreadyExists() {
        // Arrange - Create initial config
        UpdatePiiDetectionConfigCommand initialCommand = new UpdatePiiDetectionConfigCommand(
            true, true, false, new BigDecimal("0.60"), "user1"
        );
        managePiiDetectionConfigPort.updateConfig(initialCommand);

        // Act - Update config
        UpdatePiiDetectionConfigCommand updateCommand = new UpdatePiiDetectionConfigCommand(
            false, true, true, new BigDecimal("0.90"), "user2"
        );
        PiiDetectionConfig updated = managePiiDetectionConfigPort.updateConfig(updateCommand);

        // Assert
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(updated.getId()).isEqualTo(1);
        softly.assertThat(updated.isGlinerEnabled()).isFalse();
        softly.assertThat(updated.isPresidioEnabled()).isTrue();
        softly.assertThat(updated.isRegexEnabled()).isTrue();
        softly.assertThat(updated.getDefaultThreshold()).isEqualByComparingTo(new BigDecimal("0.90"));
        softly.assertThat(updated.getUpdatedBy()).isEqualTo("user2");
        softly.assertAll();

        // Verify single row in database
        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    void Should_MaintainSingleRow_When_MultipleUpdates() {
        // Arrange & Act - Multiple updates
        for (int i = 0; i < 5; i++) {
            UpdatePiiDetectionConfigCommand command = new UpdatePiiDetectionConfigCommand(
                i % 2 == 0, i % 2 != 0, true, 
                new BigDecimal("0." + (70 + i)), "user" + i
            );
            managePiiDetectionConfigPort.updateConfig(command);
        }

        // Assert - Only one row in database
        assertThat(jpaRepository.count()).isEqualTo(1);
        
        // Verify latest update
        PiiDetectionConfig config = managePiiDetectionConfigPort.getConfig();
        assertThat(config.getUpdatedBy()).isEqualTo("user4");
        assertThat(config.getDefaultThreshold()).isEqualByComparingTo(new BigDecimal("0.74"));
    }

    @Test
    void Should_PersistBoundaryThresholds_When_ThresholdIsZeroOrOne() {
        // Act - Update with threshold 0.0
        UpdatePiiDetectionConfigCommand zeroCommand = new UpdatePiiDetectionConfigCommand(
            true, false, false, BigDecimal.ZERO, "testuser"
        );
        PiiDetectionConfig zeroConfig = managePiiDetectionConfigPort.updateConfig(zeroCommand);

        // Assert
        assertThat(zeroConfig.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ZERO);

        // Act - Update with threshold 1.0
        UpdatePiiDetectionConfigCommand oneCommand = new UpdatePiiDetectionConfigCommand(
            true, false, false, BigDecimal.ONE, "testuser"
        );
        PiiDetectionConfig oneConfig = managePiiDetectionConfigPort.updateConfig(oneCommand);

        // Assert
        assertThat(oneConfig.getDefaultThreshold()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void Should_PersistDetectorStates_When_OnlyOneDetectorEnabled() {
        // Test with only GLiNER enabled
        UpdatePiiDetectionConfigCommand glinerCommand = new UpdatePiiDetectionConfigCommand(
            true, false, false, new BigDecimal("0.75"), "testuser"
        );
        PiiDetectionConfig glinerConfig = managePiiDetectionConfigPort.updateConfig(glinerCommand);
        
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(glinerConfig.isGlinerEnabled()).isTrue();
        softly.assertThat(glinerConfig.isPresidioEnabled()).isFalse();
        softly.assertThat(glinerConfig.isRegexEnabled()).isFalse();

        // Test with only Presidio enabled
        UpdatePiiDetectionConfigCommand presidioCommand = new UpdatePiiDetectionConfigCommand(
            false, true, false, new BigDecimal("0.75"), "testuser"
        );
        PiiDetectionConfig presidioConfig = managePiiDetectionConfigPort.updateConfig(presidioCommand);
        
        softly.assertThat(presidioConfig.isGlinerEnabled()).isFalse();
        softly.assertThat(presidioConfig.isPresidioEnabled()).isTrue();
        softly.assertThat(presidioConfig.isRegexEnabled()).isFalse();

        // Test with only Regex enabled
        UpdatePiiDetectionConfigCommand regexCommand = new UpdatePiiDetectionConfigCommand(
            false, false, true, new BigDecimal("0.75"), "testuser"
        );
        PiiDetectionConfig regexConfig = managePiiDetectionConfigPort.updateConfig(regexCommand);
        
        softly.assertThat(regexConfig.isGlinerEnabled()).isFalse();
        softly.assertThat(regexConfig.isPresidioEnabled()).isFalse();
        softly.assertThat(regexConfig.isRegexEnabled()).isTrue();
        
        softly.assertAll();
    }
}
