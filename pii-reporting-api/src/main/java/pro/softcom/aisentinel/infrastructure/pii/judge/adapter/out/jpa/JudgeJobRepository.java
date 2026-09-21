package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out.jpa.entity.JudgeJobEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JudgeJobRepository extends JpaRepository<JudgeJobEntity, String> {

    Optional<JudgeJobEntity> findFirstBySpaceKeyAndStatus(String spaceKey, String status);

    List<JudgeJobEntity> findByStatus(String status);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
        UPDATE JudgeJobEntity j
        SET j.status = 'INTERRUPTED', j.updatedAt = :now
        WHERE j.status = 'RUNNING'
        """)
    int markRunningAsInterrupted(@Param("now") Instant now);
}
