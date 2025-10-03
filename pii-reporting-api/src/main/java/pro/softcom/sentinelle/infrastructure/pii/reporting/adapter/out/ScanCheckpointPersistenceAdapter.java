package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
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
    public void save(ScanCheckpoint checkpoint) {
        if (checkpoint == null || isBlank(checkpoint.scanId()) || isBlank(checkpoint.spaceKey())) {
            return;
        }
        // Merge semantics: never overwrite existing lastProcessed* with null values.
        var existingOpt = jpaRepository.findByScanIdAndSpaceKey(checkpoint.scanId(), checkpoint.spaceKey());

        String lastPage = checkpoint.lastProcessedPageId();
        String lastAttachment = checkpoint.lastProcessedAttachmentName();
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            if (lastPage == null || lastPage.isBlank()) {
                lastPage = existing.getLastProcessedPageId();
            }
            if (lastAttachment == null || lastAttachment.isBlank()) {
                lastAttachment = existing.getLastProcessedAttachmentName();
            }
        }

        LocalDateTime ts = checkpoint.updatedAt() == null ? LocalDateTime.now() : checkpoint.updatedAt();
        var entity = ScanCheckpointEntity.builder()
            .scanId(checkpoint.scanId())
            .spaceKey(checkpoint.spaceKey())
            .lastProcessedPageId(lastPage)
            .lastProcessedAttachmentName(lastAttachment)
            .status(checkpoint.scanStatus().name())
            .updatedAt(ts)
            .build();
        jpaRepository.save(entity);
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
    public void deleteByScan(String scanId) {
        if (isBlank(scanId)) {
            return;
        }
        jpaRepository.deleteByScanId(scanId);
    }

    private static ScanCheckpoint toDomain(ScanCheckpointEntity e) {
        return ScanCheckpoint.builder()
            .scanId(e.getScanId())
            .spaceKey(e.getSpaceKey())
            .lastProcessedPageId(e.getLastProcessedPageId())
            .lastProcessedAttachmentName(e.getLastProcessedAttachmentName())
            .scanStatus(parseStatus(e.getStatus()))
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
