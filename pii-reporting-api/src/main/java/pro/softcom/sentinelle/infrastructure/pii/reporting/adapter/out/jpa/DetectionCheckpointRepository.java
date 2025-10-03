package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointEntity;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanCheckpointId;

@Repository
public interface DetectionCheckpointRepository extends
    JpaRepository<@NonNull ScanCheckpointEntity, @NonNull ScanCheckpointId> {

    Optional<ScanCheckpointEntity> findByScanIdAndSpaceKey(String scanId, String spaceKey);

    List<ScanCheckpointEntity> findByScanIdOrderBySpaceKey(String scanId);

    void deleteByScanId(String scanId);
}
