package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceConnectionConfigEntity;

/**
 * Spring Data JPA repository for Confluence connection configuration.
 * Provides database access for the connection configuration entity.
 */
@Repository
public interface ConfluenceConnectionConfigJpaRepository extends JpaRepository<ConfluenceConnectionConfigEntity, Integer> {
}
