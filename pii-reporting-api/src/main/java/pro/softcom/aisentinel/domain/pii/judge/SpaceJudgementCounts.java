package pro.softcom.aisentinel.domain.pii.judge;

/**
 * Per-space tally of the findings the judge has already evaluated and of those still parked
 * as proposed false positives awaiting a reviewer.
 */
public record SpaceJudgementCounts(String spaceKey, long judged, long proposedFalsePositives) {
}
