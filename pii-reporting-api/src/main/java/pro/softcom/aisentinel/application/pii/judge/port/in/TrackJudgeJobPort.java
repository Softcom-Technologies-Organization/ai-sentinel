package pro.softcom.aisentinel.application.pii.judge.port.in;

import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;

import java.util.List;
import java.util.Optional;

/**
 * Port IN to follow judge jobs: polling, interruption and the per-space dashboard summary.
 */
public interface TrackJudgeJobPort {

    Optional<JudgeJob> findJob(String jobId);

    /**
     * Interrupts a running job; verdicts already recorded are kept.
     */
    void cancel(String jobId);

    List<JudgeSpaceSummary> spaceSummaries();
}
