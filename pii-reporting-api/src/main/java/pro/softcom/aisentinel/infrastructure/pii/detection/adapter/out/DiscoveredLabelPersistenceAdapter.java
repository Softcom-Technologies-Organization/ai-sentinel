package pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out;

import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.detection.port.out.DiscoveredLabelStore;
import pro.softcom.aisentinel.domain.pii.detection.DiscoveredLabel;
import pro.softcom.aisentinel.domain.pii.detection.DiscoveredLabelStatus;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.entity.DiscoveredLabelEntity;
import pro.softcom.aisentinel.infrastructure.pii.detection.adapter.out.jpa.DiscoveredLabelJpaRepository;

import java.util.List;
import java.util.Map;

/**
 * Persistence adapter for MINISTRAL discovered labels.
 * <p>
 * Implements the store port and delegates to Spring Data JPA. Each occurrence
 * upsert runs in its own transaction (the repository method is transactional),
 * so a concurrent increment on another label never blocks or rolls back this one.
 */
@Component
public class DiscoveredLabelPersistenceAdapter implements DiscoveredLabelStore {

    private final DiscoveredLabelJpaRepository jpaRepository;

    public DiscoveredLabelPersistenceAdapter(DiscoveredLabelJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void recordOccurrences(Map<String, Integer> labelCounts) {
        labelCounts.forEach(jpaRepository::upsertOccurrence);
    }

    @Override
    public List<DiscoveredLabel> findByStatus(DiscoveredLabelStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(DiscoveredLabelEntity::toDomain)
                .toList();
    }

    @Override
    public void markPromoted(String label) {
        jpaRepository.updateStatusByLabel(label, DiscoveredLabelStatus.PROMOTED.name());
    }

    @Override
    public void markIgnored(String label) {
        jpaRepository.updateStatusByLabel(label, DiscoveredLabelStatus.IGNORED.name());
    }
}
