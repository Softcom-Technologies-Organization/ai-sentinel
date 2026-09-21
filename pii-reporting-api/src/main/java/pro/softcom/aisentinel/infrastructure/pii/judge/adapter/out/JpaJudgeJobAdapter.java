package pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.application.pii.judge.port.out.JudgeJobStore;
import pro.softcom.aisentinel.domain.pii.judge.JudgeApplyMode;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobAlreadyRunningException;
import pro.softcom.aisentinel.domain.pii.judge.JudgeJobStatus;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out.jpa.JudgeJobRepository;
import pro.softcom.aisentinel.infrastructure.pii.judge.adapter.out.jpa.entity.JudgeJobEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed implementation of the judge job journal.
 */
@Component
@RequiredArgsConstructor
public class JpaJudgeJobAdapter implements JudgeJobStore {

    private static final String RUNNING_JOB_CONSTRAINT = "uq_pii_judge_job_running_per_space";

    private final JudgeJobRepository repository;

    /**
     * @throws JudgeJobAlreadyRunningException when the partial unique index on RUNNING jobs
     *                                         per space rejects the insert
     */
    @Override
    @Transactional
    public void create(JudgeJob job) {
        try {
            repository.saveAndFlush(toEntity(job));
        } catch (DataIntegrityViolationException e) {
            if (isRunningJobConflict(e)) {
                throw new JudgeJobAlreadyRunningException(job.spaceKey());
            }
            throw e;
        }
    }

    private static boolean isRunningJobConflict(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return RUNNING_JOB_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void update(JudgeJob job) {
        repository.save(toEntity(job));
    }

    @Override
    public Optional<JudgeJob> findById(String jobId) {
        return repository.findById(jobId).map(JpaJudgeJobAdapter::toDomain);
    }

    @Override
    public Optional<JudgeJob> findActiveBySpace(String spaceKey) {
        return repository.findFirstBySpaceKeyAndStatus(spaceKey, JudgeJobStatus.RUNNING.name())
                .map(JpaJudgeJobAdapter::toDomain);
    }

    @Override
    public List<JudgeJob> findAllActive() {
        return repository.findByStatus(JudgeJobStatus.RUNNING.name()).stream()
                .map(JpaJudgeJobAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int markInterruptedOnBoot() {
        return repository.markRunningAsInterrupted(Instant.now());
    }

    private static JudgeJobEntity toEntity(JudgeJob job) {
        return JudgeJobEntity.builder()
                .id(job.id())
                .spaceKey(job.spaceKey())
                .status(job.status().name())
                .model(job.model())
                .applyMode(job.applyMode().name())
                .confidenceThreshold(job.confidenceThreshold())
                .processed(job.processed())
                .total(job.total())
                .falsePositives(job.falsePositives())
                .errors(job.errors())
                .actor(job.actor())
                .createdAt(job.createdAt())
                .updatedAt(job.updatedAt())
                .build();
    }

    private static JudgeJob toDomain(JudgeJobEntity entity) {
        return JudgeJob.builder()
                .id(entity.getId())
                .spaceKey(entity.getSpaceKey())
                .status(JudgeJobStatus.valueOf(entity.getStatus()))
                .model(entity.getModel())
                .applyMode(JudgeApplyMode.valueOf(entity.getApplyMode()))
                .confidenceThreshold(entity.getConfidenceThreshold())
                .processed(entity.getProcessed())
                .total(entity.getTotal())
                .falsePositives(entity.getFalsePositives())
                .errors(entity.getErrors())
                .actor(entity.getActor())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
