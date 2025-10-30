package pro.softcom.sentinelle.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.event.SpaceScanCompleted;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanEventDispatcher {
    private final PublishEventPort publishEventPort;

    public void scheduleAfterCommit(Runnable action) {
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

            // Not in a transaction: execute immediately
            action.run();
        }
    }

    public void publishAfterCommit(String scanId, String spaceKey) {
        scheduleAfterCommit(() -> publishEventPort.publishCompleteEvent(new SpaceScanCompleted(scanId, spaceKey)));
    }
}
