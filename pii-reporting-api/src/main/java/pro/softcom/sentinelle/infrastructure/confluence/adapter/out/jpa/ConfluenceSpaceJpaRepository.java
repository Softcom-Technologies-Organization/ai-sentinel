package pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.softcom.sentinelle.infrastructure.confluence.adapter.out.jpa.entity.ConfluenceSpaceEntity;

@Repository
public interface ConfluenceSpaceJpaRepository extends
    JpaRepository<@NonNull ConfluenceSpaceEntity, @NonNull String> {

    List<ConfluenceSpaceEntity> findAllByOrderByNameAsc();
}
