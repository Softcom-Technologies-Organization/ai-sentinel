package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanSeverityCountRepository;
import pro.softcom.aisentinel.domain.pii.reporting.ScanSeverityCount;
import pro.softcom.aisentinel.domain.pii.reporting.SeverityCounts;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.ScanSeverityCountJpaRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountEntity;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanSeverityCountId;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing {@link ScanSeverityCountRepository} port.
 * 
 * <p>Bridges the hexagonal architecture boundary between application layer
 * (domain-focused use cases) and infrastructure layer (JPA persistence).
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Maps domain objects (ScanSeverityCount, SeverityCounts) to/from JPA entities</li>
 *   <li>Delegates persistence operations to JPA repository</li>
 *   <li>Provides atomic increment operations via PostgreSQL UPSERT</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScanSeverityCountPersistenceAdapter implements ScanSeverityCountRepository {

    private final ScanSeverityCountJpaRepository jpaRepository;

    @Override
    public void incrementCounts(String scanId, String spaceKey, SeverityCounts delta) {
        jpaRepository.incrementCounts(
            scanId,
            spaceKey,
            delta.high(),
            delta.medium(),
            delta.low()
        );
    }

    @Override
    public Optional<SeverityCounts> findByScanIdAndSpaceKey(String scanId, String spaceKey) {
        var id = ScanSeverityCountId.builder()
            .scanId(scanId)
            .spaceKey(spaceKey)
            .build();
        
        return jpaRepository.findById(id)
            .map(this::toDomainCounts);
    }

    @Override
    public List<ScanSeverityCount> findByScanId(String scanId) {
        return jpaRepository.findById_ScanIdOrderById_SpaceKey(scanId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void deleteByScanId(String scanId) {
        jpaRepository.deleteById_ScanId(scanId);
    }

    /**
     * Maps JPA entity to domain SeverityCounts.
     */
    private SeverityCounts toDomainCounts(ScanSeverityCountEntity entity) {
        return new SeverityCounts(
            entity.getNbOfHighSeverity(),
            entity.getNbOfMediumSeverity(),
            entity.getNbOfLowSeverity()
        );
    }

    /**
     * Maps JPA entity to domain ScanSeverityCount.
     */
    private ScanSeverityCount toDomain(ScanSeverityCountEntity entity) {
        return new ScanSeverityCount(
            entity.getScanId(),
            entity.getSpaceKey(),
            toDomainCounts(entity)
        );
    }
}
