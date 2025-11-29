package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for PII detection configuration.
 * Provides database access for the configuration entity.
 */
@Repository
public interface PiiDetectionConfigJpaRepository extends JpaRepository<PiiDetectionConfigEntity, Integer> {
}
