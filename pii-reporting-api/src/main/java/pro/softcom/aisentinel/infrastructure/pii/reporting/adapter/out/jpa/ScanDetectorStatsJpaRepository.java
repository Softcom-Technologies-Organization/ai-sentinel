package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanDetectorStatsId;

import java.util.List;

/**
 * JPA repository for atomic per-detector scan statistics.
 *
 * <p>Detector stats are accumulated via PostgreSQL UPSERT, summing busy time,
 * processed characters and detections across the scan's analysis requests.
 */
@Repository
public interface ScanDetectorStatsJpaRepository extends
    JpaRepository<@NonNull ScanDetectorStatsEntity, @NonNull ScanDetectorStatsId>,
    ScanDetectorStatsUpsert {

    List<ScanDetectorStatsEntity> findById_ScanIdAndId_SpaceKeyOrderById_Detector(String scanId, String spaceKey);
}
