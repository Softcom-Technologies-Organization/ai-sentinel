package pro.softcom.aisentinel.application.pii.judge.port.in;

import pro.softcom.aisentinel.domain.pii.judge.JudgeJob;

/**
 * Dashboard read model of the judge for one space: what has been evaluated so far, what still
 * awaits a reviewer, and the job in flight if any.
 */
public record JudgeSpaceSummary(
        String spaceKey,
        long judgedCount,
        long proposedFalsePositives,
        JudgeJob activeJob
) {
}
