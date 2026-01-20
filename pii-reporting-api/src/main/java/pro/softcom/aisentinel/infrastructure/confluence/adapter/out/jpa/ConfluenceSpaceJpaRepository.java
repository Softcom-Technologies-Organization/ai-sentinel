package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.aisentinel.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfluenceSpaceJpaRepository extends
    JpaRepository<@NonNull ConfluenceSpaceEntity, @NonNull String> {

    List<ConfluenceSpaceEntity> findAllByOrderByNameAsc();

    Optional<ConfluenceSpaceEntity> findBySpaceKey(String spaceKey);
}
