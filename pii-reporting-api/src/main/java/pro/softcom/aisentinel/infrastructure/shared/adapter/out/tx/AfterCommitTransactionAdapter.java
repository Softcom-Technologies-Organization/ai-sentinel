package pro.softcom.aisentinel.infrastructure.shared.adapter.out.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.softcom.aisentinel.application.pii.reporting.port.out.AfterCommitExecutionPort;

/**
 * Spring-based adapter that executes actions after transaction commit.
 * Falls back to immediate execution when there is no active transaction.
 */
@Component
@Slf4j
public class AfterCommitTransactionAdapter implements AfterCommitExecutionPort {

    @Override
    public void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.error("Action failed after commit: {}", e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn("No actual transaction active, execute the action immediately");
            action.run();
        }
    }
}
