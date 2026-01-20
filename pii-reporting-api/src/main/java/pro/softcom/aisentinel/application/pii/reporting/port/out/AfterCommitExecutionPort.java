package pro.softcom.aisentinel.application.pii.reporting.port.out;

/**
 * Port to execute an action after the current transaction commits.
 * If no transaction is active, the action should be executed immediately.
 *
 * Business purpose: ensure side-effects (like publishing domain events) are only
 * visible once the transactional state has been durably persisted.
 */
public interface AfterCommitExecutionPort {
    void runAfterCommit(Runnable action);
}
