package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.reporting.port.out.AfterCommitExecutionPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.domain.pii.scan.SpaceScanCompleted;

/**
 * Orchestrates publication of domain events related to scan lifecycle.
 * Business rule: publish completion event only after transaction commit.
 *
 * This class is framework-agnostic; transaction timing is delegated to a port.
 */
@RequiredArgsConstructor
@Slf4j
public class ScanEventDispatcher {
    private final PublishEventPort publishEventPort;
    private final AfterCommitExecutionPort afterCommitExecutionPort;

    public void scheduleAfterCommit(Runnable action) {
        afterCommitExecutionPort.runAfterCommit(() -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Action failed after commit: {}", e.getMessage(), e);
            }
        });
    }

    public void publishAfterCommit(String scanId, String spaceKey) {
        scheduleAfterCommit(() -> publishEventPort.publishCompleteEvent(new SpaceScanCompleted(scanId, spaceKey)));
    }
}
