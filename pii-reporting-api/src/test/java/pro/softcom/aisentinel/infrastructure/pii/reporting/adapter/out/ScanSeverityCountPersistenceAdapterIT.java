package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSeverityCount;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanSeverityCountJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountId;

/**
 * Integration test for {@link ScanSeverityCountPersistenceAdapter}.
 * 
 * <p>Tests the persistence layer with a real PostgreSQL database using Testcontainers.
 * Verifies critical atomic increment operations and query methods.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScanSeverityCountPersistenceAdapter.class)
class ScanSeverityCountPersistenceAdapterIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        // Ensure container is started before Spring context builds
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
    private ScanSeverityCountJpaRepository jpaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private ScanSeverityCountPersistenceAdapter adapter;

    @BeforeEach
    void cleanDb() {
        // Ensure a clean state for each test
        jpaRepository.deleteAll();
        em.flush();
    }

    @Test
    void Should_IncrementCounts_When_NewRecord() {
        // Arrange
        var delta = new SeverityCounts(3, 5, 2);

        // Act
        adapter.incrementCounts("scan-001", "SPACE-A", delta);

        // Assert
        var id = ScanSeverityCountId.builder()
            .scanId("scan-001")
            .spaceKey("SPACE-A")
            .build();
        var saved = jpaRepository.findById(id);
        
        assertThat(saved).isPresent();
        var entity = saved.orElseThrow();
        
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity.getScanId()).isEqualTo("scan-001");
        softly.assertThat(entity.getSpaceKey()).isEqualTo("SPACE-A");
        softly.assertThat(entity.getNbOfHighSeverity()).isEqualTo(3);
        softly.assertThat(entity.getNbOfMediumSeverity()).isEqualTo(5);
        softly.assertThat(entity.getNbOfLowSeverity()).isEqualTo(2);
        softly.assertAll();
    }

    @Test
    void Should_IncrementCounts_When_ExistingRecord() {
        // Arrange - Create initial record
        var initialEntity = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-002")
                .spaceKey("SPACE-B")
                .build())
            .nbOfHighSeverity(10)
            .nbOfMediumSeverity(20)
            .nbOfLowSeverity(30)
            .build();
        em.persist(initialEntity);
        em.flush();
        em.clear(); // Clear to ensure we read fresh data

        // Act - Increment with new delta
        var delta = new SeverityCounts(3, 5, 2);
        adapter.incrementCounts("scan-002", "SPACE-B", delta);

        // Assert - Verify atomic increment
        var id = ScanSeverityCountId.builder()
            .scanId("scan-002")
            .spaceKey("SPACE-B")
            .build();
        var updated = jpaRepository.findById(id).orElseThrow();
        
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(updated.getNbOfHighSeverity())
            .as("High severity should be incremented from 10 by 3")
            .isEqualTo(13);
        softly.assertThat(updated.getNbOfMediumSeverity())
            .as("Medium severity should be incremented from 20 by 5")
            .isEqualTo(25);
        softly.assertThat(updated.getNbOfLowSeverity())
            .as("Low severity should be incremented from 30 by 2")
            .isEqualTo(32);
        softly.assertAll();
    }

    @Test
    void Should_IncrementCounts_AtomicallyWithZeroDeltas() {
        // Arrange
        var delta = new SeverityCounts(0, 0, 0);

        // Act
        adapter.incrementCounts("scan-003", "SPACE-C", delta);

        // Assert - Should create record with zeros
        var id = ScanSeverityCountId.builder()
            .scanId("scan-003")
            .spaceKey("SPACE-C")
            .build();
        var saved = jpaRepository.findById(id);
        
        assertThat(saved).isPresent();
        var entity = saved.orElseThrow();
        
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(entity.getNbOfHighSeverity()).isZero();
        softly.assertThat(entity.getNbOfMediumSeverity()).isZero();
        softly.assertThat(entity.getNbOfLowSeverity()).isZero();
        softly.assertAll();
    }

    @Test
    void Should_FindByScanIdAndSpaceKey_When_RecordExists() {
        // Arrange
        var entity = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-004")
                .spaceKey("SPACE-D")
                .build())
            .nbOfHighSeverity(7)
            .nbOfMediumSeverity(14)
            .nbOfLowSeverity(21)
            .build();
        em.persist(entity);
        em.flush();

        // Act
        var result = adapter.findByScanIdAndSpaceKey("scan-004", "SPACE-D");

        // Assert
        assertThat(result).isPresent();
        var counts = result.orElseThrow();
        
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(counts.high()).isEqualTo(7);
        softly.assertThat(counts.medium()).isEqualTo(14);
        softly.assertThat(counts.low()).isEqualTo(21);
        softly.assertThat(counts.total()).isEqualTo(42);
        softly.assertAll();
    }

    @Test
    void Should_FindByScanIdAndSpaceKey_ReturnEmpty_When_RecordNotExists() {
        // Act
        var result = adapter.findByScanIdAndSpaceKey("scan-999", "SPACE-NONE");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void Should_FindByScanId_ReturnOrderedList_When_MultipleSpaces() {
        // Arrange - Create records in non-alphabetical order
        var entityC = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-005")
                .spaceKey("SPACE-C")
                .build())
            .nbOfHighSeverity(1)
            .nbOfMediumSeverity(2)
            .nbOfLowSeverity(3)
            .build();
        var entityA = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-005")
                .spaceKey("SPACE-A")
                .build())
            .nbOfHighSeverity(4)
            .nbOfMediumSeverity(5)
            .nbOfLowSeverity(6)
            .build();
        var entityB = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-005")
                .spaceKey("SPACE-B")
                .build())
            .nbOfHighSeverity(7)
            .nbOfMediumSeverity(8)
            .nbOfLowSeverity(9)
            .build();
        
        // Persist in non-alphabetical order
        em.persist(entityC);
        em.persist(entityA);
        em.persist(entityB);
        em.flush();

        // Act
        var results = adapter.findByScanId("scan-005");

        // Assert - Should be ordered by space key alphabetically
        assertThat(results).hasSize(3);
        assertThat(results)
            .extracting(ScanSeverityCount::spaceKey)
            .containsExactly("SPACE-A", "SPACE-B", "SPACE-C");
        
        // Verify counts are correct
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(results.get(0).counts().high()).isEqualTo(4);
        softly.assertThat(results.get(1).counts().high()).isEqualTo(7);
        softly.assertThat(results.get(2).counts().high()).isEqualTo(1);
        softly.assertAll();
    }

    @Test
    void Should_FindByScanId_ReturnEmptyList_When_NoRecords() {
        // Act
        var results = adapter.findByScanId("scan-999");

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    void Should_FindByScanId_ReturnOnlyMatchingScanId() {
        // Arrange - Create records for different scans
        var entityScan1 = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-006")
                .spaceKey("SPACE-A")
                .build())
            .nbOfHighSeverity(1)
            .nbOfMediumSeverity(2)
            .nbOfLowSeverity(3)
            .build();
        var entityScan2 = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-007")
                .spaceKey("SPACE-A")
                .build())
            .nbOfHighSeverity(4)
            .nbOfMediumSeverity(5)
            .nbOfLowSeverity(6)
            .build();
        
        em.persist(entityScan1);
        em.persist(entityScan2);
        em.flush();

        // Act
        var results = adapter.findByScanId("scan-006");

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).scanId()).isEqualTo("scan-006");
        assertThat(results.get(0).spaceKey()).isEqualTo("SPACE-A");
    }

    @Test
    void Should_DeleteByScanId_RemoveAllRows_When_MultipleSpaces() {
        // Arrange - Create multiple records for same scan
        var entity1 = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-008")
                .spaceKey("SPACE-A")
                .build())
            .nbOfHighSeverity(1)
            .nbOfMediumSeverity(2)
            .nbOfLowSeverity(3)
            .build();
        var entity2 = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-008")
                .spaceKey("SPACE-B")
                .build())
            .nbOfHighSeverity(4)
            .nbOfMediumSeverity(5)
            .nbOfLowSeverity(6)
            .build();
        var entityOther = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-009")
                .spaceKey("SPACE-C")
                .build())
            .nbOfHighSeverity(7)
            .nbOfMediumSeverity(8)
            .nbOfLowSeverity(9)
            .build();
        
        em.persist(entity1);
        em.persist(entity2);
        em.persist(entityOther);
        em.flush();

        // Act
        adapter.deleteByScanId("scan-008");

        // Assert
        var remainingForScan = jpaRepository.findById_ScanIdOrderById_SpaceKey("scan-008");
        var remainingOthers = jpaRepository.findById_ScanIdOrderById_SpaceKey("scan-009");
        
        assertThat(remainingForScan).isEmpty();
        assertThat(remainingOthers).hasSize(1);
        assertThat(remainingOthers.get(0).getScanId()).isEqualTo("scan-009");
    }

    @Test
    void Should_DeleteByScanId_DoNothing_When_ScanNotExists() {
        // Arrange
        var entity = ScanSeverityCountEntity.builder()
            .id(ScanSeverityCountId.builder()
                .scanId("scan-010")
                .spaceKey("SPACE-A")
                .build())
            .nbOfHighSeverity(1)
            .nbOfMediumSeverity(2)
            .nbOfLowSeverity(3)
            .build();
        em.persist(entity);
        em.flush();

        // Act - Delete non-existing scan
        adapter.deleteByScanId("scan-999");

        // Assert - Original record should still exist
        var remaining = jpaRepository.findById_ScanIdOrderById_SpaceKey("scan-010");
        assertThat(remaining).hasSize(1);
    }
}
