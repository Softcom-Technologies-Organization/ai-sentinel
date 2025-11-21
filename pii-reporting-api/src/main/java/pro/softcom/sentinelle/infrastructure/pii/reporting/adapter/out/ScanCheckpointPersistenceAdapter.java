
package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.sentinelle.application.pii.scan.port.out.ScanCheckpointRepository;
import pro.softcom.sentinelle.domain.pii.ScanStatus;
import pro.softcom.sentinelle.domain.pii.reporting.ScanCheckpoint;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;

/**
 * PostgreSQL-backed implementation of ScanCheckpointRepository. Business intent: persist
 * fine-grained resume positions (page/attachment) per scan & space. Now implemented with Spring
 * Data JPA/Hibernate for simplicity and maintainability.
 */
@Component
public class ScanCheckpointPersistenceAdapter implements ScanCheckpointRepository {

    private final DetectionCheckpointRepository jpaRepository;

    public ScanCheckpointPersistenceAdapter(DetectionCheckpointRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(ScanCheckpoint checkpoint) {
        if (checkpoint == null || isBlank(checkpoint.scanId()) || isBlank(checkpoint.spaceKey())) {
            return;
        }

        LocalDateTime lastUpdated = checkpoint.updatedAt() == null ? LocalDateTime.now() : checkpoint.updatedAt();

        // Use PostgreSQL UPSERT (INSERT ... ON CONFLICT DO UPDATE) for atomic operation
        jpaRepository.upsertCheckpoint(
            checkpoint.scanId(),
            checkpoint.spaceKey(),
            checkpoint.lastProcessedPageId(),
            checkpoint.lastProcessedAttachmentName(),
            checkpoint.scanStatus().name(),
            checkpoint.progressPercentage(),
            lastUpdated
        );
    }

    @Override
    public Optional<ScanCheckpoint> findByScanAndSpace(String scanId, String spaceKey) {
        if (isBlank(scanId) || isBlank(spaceKey)) {
            return Optional.empty();
        }
        return jpaRepository.findByScanIdAndSpaceKey(scanId, spaceKey)
            .map(ScanCheckpointPersistenceAdapter::toDomain);
    }

    @Override
    public List<ScanCheckpoint> findByScan(String scanId) {
        if (isBlank(scanId)) {
            return List.of();
        }
        return jpaRepository.findByScanIdOrderBySpaceKey(scanId).stream()
            .map(ScanCheckpointPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<ScanCheckpoint> findBySpace(String spaceKey) {
        if (isBlank(spaceKey)) {
            return List.of();
        }
        return jpaRepository.findBySpaceKeyOrderByUpdatedAtDesc(spaceKey).stream()
            .map(ScanCheckpointPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<ScanCheckpoint> findLatestBySpace(String spaceKey) {
        if (isBlank(spaceKey)) {
            return Optional.empty();
        }
        return jpaRepository.findFirstBySpaceKeyOrderByUpdatedAtDesc(spaceKey)
            .map(ScanCheckpointPersistenceAdapter::toDomain);
    }

    @Override
    public void deleteByScan(String scanId) {
        if (isBlank(scanId)) {
            return;
        }
        jpaRepository.deleteByScanId(scanId);
    }

    public static ScanCheckpoint toDomain(ScanCheckpointEntity e) {
        return ScanCheckpoint.builder()
            .scanId(e.getScanId())
            .spaceKey(e.getSpaceKey())
            .lastProcessedPageId(e.getLastProcessedPageId())
            .lastProcessedAttachmentName(e.getLastProcessedAttachmentName())
            .scanStatus(parseStatus(e.getStatus()))
            .progressPercentage(e.getProgressPercentage())
            .updatedAt(e.getUpdatedAt())
            .build();
    }

    private static ScanStatus parseStatus(String s) {
        try {
            return ScanStatus.valueOf(s);
        } catch (Exception _) {
            return ScanStatus.RUNNING;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
