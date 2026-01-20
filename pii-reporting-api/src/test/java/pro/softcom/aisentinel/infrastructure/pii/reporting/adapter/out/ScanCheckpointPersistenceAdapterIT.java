package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import jakarta.persistence.EntityManager;
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
import pro.softcom.aisentinel.domain.pii.ScanStatus;
import pro.softcom.aisentinel.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScanCheckpointPersistenceAdapter.class)
class ScanCheckpointPersistenceAdapterIT {

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
    private DetectionCheckpointRepository jpaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private ScanCheckpointPersistenceAdapter scanCheckpointPersistenceAdapter;

    @BeforeEach
    void cleanDb() {
        // ensure a clean state for each test
        jpaRepository.deleteAll();
        em.flush();
    }

    @Test
    void Should_Save_InsertEntity_When_NewCheckpoint() {
        var ts = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        var cp = ScanCheckpoint.builder()
            .scanId("scan-10")
            .spaceKey("SPACE-A")
            .lastProcessedPageId("p1")
            .lastProcessedAttachmentName("a1")
            .scanStatus(ScanStatus.RUNNING)
            .updatedAt(ts)
            .build();

        scanCheckpointPersistenceAdapter.save(cp);

        var saved = jpaRepository.findByScanIdAndSpaceKey("scan-10", "SPACE-A");
        assertThat(saved).isPresent();
        var e = saved.orElseThrow();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(e.getScanId()).isEqualTo("scan-10");
        softly.assertThat(e.getSpaceKey()).isEqualTo("SPACE-A");
        softly.assertThat(e.getLastProcessedPageId()).isEqualTo("p1");
        softly.assertThat(e.getLastProcessedAttachmentName()).isEqualTo("a1");
        softly.assertThat(e.getStatus()).isEqualTo("RUNNING");
        softly.assertThat(e.getUpdatedAt()).isEqualTo(ts);
        softly.assertAll();
    }

    @Test
    void Should_Save_MergeLastProcessedFields_When_IncomingNulls() {
        // seed existing entity with both fields set
        var base = ScanCheckpointEntity.builder()
            .scanId("scan-11")
            .spaceKey("SPACE-A")
            .lastProcessedPageId("p-last")
            .lastProcessedAttachmentName("a-last")
            .status("COMPLETED")
            .updatedAt(LocalDateTime.of(2024, 1, 1, 10, 0))
            .build();
        em.persist(base);
        em.flush();

        // now save a checkpoint with null last* fields => they must be preserved (merge semantics)
        var incoming = ScanCheckpoint.builder()
            .scanId("scan-11")
            .spaceKey("SPACE-A")
            .lastProcessedPageId(null)
            .lastProcessedAttachmentName(null)
            .scanStatus(ScanStatus.FAILED)
            .updatedAt(LocalDateTime.of(2024, 1, 1, 11, 0))
            .build();

        scanCheckpointPersistenceAdapter.save(incoming);

        var after = jpaRepository.findByScanIdAndSpaceKey("scan-11", "SPACE-A").orElseThrow();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(after.getLastProcessedPageId()).isEqualTo("p-last");
        softly.assertThat(after.getLastProcessedAttachmentName()).isEqualTo("a-last");
        // status updated to latest
        softly.assertThat(after.getStatus()).isEqualTo("FAILED");
        softly.assertThat(after.getUpdatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 1, 11, 0));
        softly.assertAll();
    }

    @Test
    void Should_FindByScanAndSpace_ReturnDomain_When_Existing() {
        var entity = ScanCheckpointEntity.builder()
            .scanId("scan-12")
            .spaceKey("SPACE-B")
            .lastProcessedPageId("p-1")
            .lastProcessedAttachmentName("a-1")
            .status("COMPLETED")
            .updatedAt(LocalDateTime.of(2024, 2, 1, 9, 0))
            .build();
        em.persist(entity);
        em.flush();

        var opt = scanCheckpointPersistenceAdapter.findByScanAndSpace("scan-12", "SPACE-B");
        assertThat(opt).isPresent();
        var cp = opt.orElseThrow();
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(cp.scanId()).isEqualTo("scan-12");
        softly.assertThat(cp.spaceKey()).isEqualTo("SPACE-B");
        softly.assertThat(cp.lastProcessedPageId()).isEqualTo("p-1");
        softly.assertThat(cp.lastProcessedAttachmentName()).isEqualTo("a-1");
        softly.assertThat(cp.scanStatus()).isEqualTo(ScanStatus.COMPLETED);
        softly.assertAll();
    }

    @Test
    void Should_FindByScan_ReturnOrderedList_When_Existing() {
        var e1 = ScanCheckpointEntity.builder()
            .scanId("scan-13")
            .spaceKey("A")
            .status("COMPLETED")
            .updatedAt(LocalDateTime.now().minusMinutes(2))
            .build();
        var e2 = ScanCheckpointEntity.builder()
            .scanId("scan-13")
            .spaceKey("B")
            .status("FAILED")
            .updatedAt(LocalDateTime.now())
            .build();
        em.persist(e2);
        em.persist(e1);
        em.flush();

        var list = scanCheckpointPersistenceAdapter.findByScan("scan-13");
        assertThat(list).hasSize(2);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(list.get(0).spaceKey()).isEqualTo("A");
        softly.assertThat(list.get(0).scanStatus()).isEqualTo(ScanStatus.COMPLETED);
        softly.assertThat(list.get(1).spaceKey()).isEqualTo("B");
        softly.assertThat(list.get(1).scanStatus()).isEqualTo(ScanStatus.FAILED);
        softly.assertAll();
    }

    @Test
    void Should_FindBySpace_ReturnOrderedList_When_Existing() {
        var now = LocalDateTime.now();
        var older = ScanCheckpointEntity.builder()
            .scanId("scan-14-1")
            .spaceKey("SPACE-C")
            .status("RUNNING")
            .updatedAt(now.minusMinutes(10))
            .build();
        var newer = ScanCheckpointEntity.builder()
            .scanId("scan-14-2")
            .spaceKey("SPACE-C")
            .status("FAILED")
            .updatedAt(now)
            .build();
        em.persist(older);
        em.persist(newer);
        em.flush();

        var list = scanCheckpointPersistenceAdapter.findBySpace("SPACE-C");
        assertThat(list).extracting(ScanCheckpoint::scanId).isEqualTo(List.of("scan-14-2", "scan-14-1"));
        assertThat(list).extracting(ScanCheckpoint::scanStatus).containsExactly(ScanStatus.FAILED, ScanStatus.RUNNING);
    }

    @Test
    void Should_DeleteByScan_RemoveAllRows_When_ScanIdExists() {
        var e1 = ScanCheckpointEntity.builder().scanId("scan-15").spaceKey("A").status("RUNNING").updatedAt(LocalDateTime.now()).build();
        var e2 = ScanCheckpointEntity.builder().scanId("scan-15").spaceKey("B").status("RUNNING").updatedAt(LocalDateTime.now()).build();
        var eOther = ScanCheckpointEntity.builder().scanId("other").spaceKey("C").status("RUNNING").updatedAt(LocalDateTime.now()).build();
        em.persist(e1);
        em.persist(e2);
        em.persist(eOther);
        em.flush();

        scanCheckpointPersistenceAdapter.deleteByScan("scan-15");

        var remainingForScan = jpaRepository.findByScanIdOrderBySpaceKey("scan-15");
        var remainingOthers = jpaRepository.findByScanIdOrderBySpaceKey("other");
        assertThat(remainingForScan).isEmpty();
        assertThat(remainingOthers).hasSize(1);
    }
}
