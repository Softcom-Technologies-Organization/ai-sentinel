package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.detection.port.out.PiiTypeConfigRepository;
import pro.softcom.aisentinel.domain.pii.detection.PiiTypeConfig;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity.PiiTypeConfigEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.PiiTypeConfigJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter for PII type configurations.
 * <p>
 * Implements the repository port and delegates to Spring Data JPA repository.
 */
@Component
public class PiiTypeConfigPersistenceAdapter implements PiiTypeConfigRepository {

    private final PiiTypeConfigJpaRepository jpaRepository;

    public PiiTypeConfigPersistenceAdapter(PiiTypeConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PiiTypeConfig> findAll() {
        return jpaRepository.findAll().stream()
                .map(PiiTypeConfigEntity::toDomain)
                .toList();
    }

    @Override
    public List<PiiTypeConfig> findByDetector(String detector) {
        return jpaRepository.findByDetector(detector).stream()
                .map(PiiTypeConfigEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<PiiTypeConfig> findByPiiTypeAndDetector(String piiType, String detector) {
        return jpaRepository.findByPiiTypeAndDetector(piiType, detector)
                .map(PiiTypeConfigEntity::toDomain);
    }

    @Override
    public PiiTypeConfig save(PiiTypeConfig config) {
        PiiTypeConfigEntity entity = PiiTypeConfigEntity.fromDomain(config);
        PiiTypeConfigEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<PiiTypeConfig> saveAll(List<PiiTypeConfig> configs) {
        List<PiiTypeConfigEntity> entities = configs.stream()
                .map(PiiTypeConfigEntity::fromDomain)
                .toList();
        List<PiiTypeConfigEntity> saved = jpaRepository.saveAll(entities);
        return saved.stream()
                .map(PiiTypeConfigEntity::toDomain)
                .toList();
    }

    @Override
    public boolean exists() {
        return jpaRepository.count() > 0;
    }
}
