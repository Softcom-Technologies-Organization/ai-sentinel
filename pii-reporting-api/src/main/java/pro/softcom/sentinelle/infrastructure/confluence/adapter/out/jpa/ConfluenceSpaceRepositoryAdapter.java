package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.confluence.port.out.ConfluenceSpaceRepository;
import pro.softcom.sentinelle.domain.confluence.ConfluenceSpace;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa.mapper.ConfluenceSpaceEntityMapper;

/**
 * Adapter JPA implementing the repository port for Confluence space caching.
 * Business purpose: provides database persistence for space metadata cache.
 */
@Component
@RequiredArgsConstructor
public class ConfluenceSpaceRepositoryAdapter implements ConfluenceSpaceRepository {

    private final ConfluenceSpaceJpaRepository jpaRepository;

    @Override
    public List<ConfluenceSpace> findAll() {
        return jpaRepository.findAllByOrderByNameAsc()
            .stream()
            .map(ConfluenceSpaceEntityMapper::toDomain)
            .toList();
    }

    @Override
    public void saveAll(List<ConfluenceSpace> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return;
        }
        var entities = spaces.stream()
            .map(ConfluenceSpaceEntityMapper::toEntity)
            .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<ConfluenceSpace> findStaleSpaces(LocalDateTime cutoffTime) {
        return jpaRepository.findStaleSpaces(cutoffTime)
            .stream()
            .map(ConfluenceSpaceEntityMapper::toDomain)
            .toList();
    }
}
