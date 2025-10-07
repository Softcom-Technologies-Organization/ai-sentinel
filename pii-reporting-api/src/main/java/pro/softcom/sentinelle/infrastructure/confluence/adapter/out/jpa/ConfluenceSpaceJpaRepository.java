package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

/**
 * Spring Data JPA repository for Confluence space cache operations.
 * Business purpose: provides database access for cached space data with staleness detection.
 */
@Repository
public interface ConfluenceSpaceJpaRepository extends
    JpaRepository<@NonNull ConfluenceSpaceEntity, @NonNull String> {

    /**
     * Finds all spaces ordered by name ascending.
     * Business purpose: provides alphabetically sorted space list for UI display.
     */
    List<ConfluenceSpaceEntity> findAllByOrderByNameAsc();

    /**
     * Finds spaces that need refresh based on last update timestamp.
     * Used by background refresh service to update stale cache entries.
     */
    @Query("SELECT s FROM ConfluenceSpaceEntity s WHERE s.lastUpdated < :cutoffTime")
    List<ConfluenceSpaceEntity> findStaleSpaces(@Param("cutoffTime") LocalDateTime cutoffTime);
}
