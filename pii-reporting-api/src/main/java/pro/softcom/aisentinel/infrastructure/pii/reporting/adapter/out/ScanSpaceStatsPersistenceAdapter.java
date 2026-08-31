package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSpaceStatsRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStat;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSpaceStats;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanDetectorStatsJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanSpaceStatsJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSpaceStatsEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSpaceStatsId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing {@link ScanSpaceStatsRepository}.
 *
 * <p>Delegates increments to native upsert queries (atomic, concurrency-safe)
 * and maps the read entities to domain read models.
 */
@Component
@RequiredArgsConstructor
public class ScanSpaceStatsPersistenceAdapter implements ScanSpaceStatsRepository {

    private final ScanSpaceStatsJpaRepository spaceStatsRepository;
    private final ScanDetectorStatsJpaRepository detectorStatsRepository;

    @Override
    public void markStarted(String scanId, String spaceKey, Instant startedAt) {
        spaceStatsRepository.markStarted(scanId, spaceKey, startedAt);
    }

    @Override
    public void markFinished(String scanId, String spaceKey, Instant finishedAt) {
        spaceStatsRepository.markFinished(scanId, spaceKey, finishedAt);
    }

    @Override
    public void incrementPageScanned(String scanId, String spaceKey, long charCount) {
        spaceStatsRepository.incrementPageScanned(scanId, spaceKey, charCount);
    }

    @Override
    public void incrementPageFailed(String scanId, String spaceKey) {
        spaceStatsRepository.incrementPageFailed(scanId, spaceKey);
    }

    @Override
    public void incrementAttachmentScanned(String scanId, String spaceKey, long charCount) {
        spaceStatsRepository.incrementAttachmentScanned(scanId, spaceKey, charCount);
    }

    @Override
    public void incrementAttachmentFailed(String scanId, String spaceKey) {
        spaceStatsRepository.incrementAttachmentFailed(scanId, spaceKey);
    }

    @Override
    public void accumulateDetectorStat(String scanId, String spaceKey, ScanDetectorStatDelta delta) {
        detectorStatsRepository.accumulate(scanId, spaceKey, delta);
    }

    @Override
    public Optional<ScanSpaceStats> findStats(String scanId, String spaceKey) {
        ScanSpaceStatsId id = ScanSpaceStatsId.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .build();
        return spaceStatsRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ScanDetectorStat> findDetectorStats(String scanId, String spaceKey) {
        return detectorStatsRepository
            .findById_ScanIdAndId_SpaceKeyOrderById_Detector(scanId, spaceKey).stream()
            .map(this::toDomain)
            .toList();
    }

    private ScanSpaceStats toDomain(ScanSpaceStatsEntity entity) {
        return new ScanSpaceStats(
            entity.getId().getScanId(),
            entity.getId().getSpaceKey(),
            entity.getStartedAt(),
            entity.getFinishedAt(),
            entity.getPagesScanned(),
            entity.getPagesFailed(),
            entity.getPageChars(),
            entity.getAttachmentsScanned(),
            entity.getAttachmentsFailed(),
            entity.getAttachmentChars(),
            List.of(),
            List.of());
    }

    private ScanDetectorStat toDomain(ScanDetectorStatsEntity entity) {
        return new ScanDetectorStat(
            entity.getId().getDetector(),
            entity.getDetections(),
            entity.getCharsProcessed(),
            entity.getBusyMs(),
            entity.getDiscarded(),
            entity.getFailedRequests() != null ? entity.getFailedRequests() : 0,
            entity.getLastError());
    }
}
