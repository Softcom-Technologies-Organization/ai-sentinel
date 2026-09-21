package pro.softcom.aisentinel.application.pii.judge.port.out;

import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the judge job journal. The single-RUNNING-job-per-space invariant is
 * enforced by a partial unique index on the underlying table.
 */
public interface JudgeJobStore {

    void create(JudgeJob job);

    void update(JudgeJob job);

    Optional<JudgeJob> findById(String jobId);

    Optional<JudgeJob> findActiveBySpace(String spaceKey);

    List<JudgeJob> findAllActive();

    /**
     * Marks every {@code RUNNING} job as {@code INTERRUPTED} during boot recovery.
     *
     * @return the number of jobs transitioned
     */
    int markInterruptedOnBoot();
}
