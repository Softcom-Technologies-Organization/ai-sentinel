package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.PersonallyIdentifiableInformationType;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity.PiiTypeConfigEntity;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for PII type configurations.
 */
@Repository
public interface PiiTypeConfigJpaRepository extends JpaRepository<PiiTypeConfigEntity, Long> {

    /**
     * Finds all configurations for a specific detector.
     *
     * @param detector the detector name
     * @return list of configurations
     */
    List<PiiTypeConfigEntity> findByDetector(String detector);

    /**
     * Finds configuration for a specific PII type and detector combination.
     *
     * @param piiType  the PII type identifier
     * @param detector the detector name
     * @return optional containing the configuration if found
     */
    Optional<PiiTypeConfigEntity> findByPiiTypeAndDetector(PersonallyIdentifiableInformationType piiType, String detector);
}