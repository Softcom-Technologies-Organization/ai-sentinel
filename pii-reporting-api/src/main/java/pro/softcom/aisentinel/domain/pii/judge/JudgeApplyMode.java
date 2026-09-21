package pro.softcom.aisentinel.domain.pii.judge;

/**
 * What a job does with a rejection: {@code PROPOSE} parks the finding as
 * {@code PROPOSED_FALSE_POSITIVE} until a reviewer confirms it, {@code AUTO_APPLY} reports it
 * as {@code FALSE_POSITIVE} straight away.
 */
public enum JudgeApplyMode {
    PROPOSE,
    AUTO_APPLY
}
