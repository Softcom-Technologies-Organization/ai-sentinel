package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pro.softcom.sentinelle.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.sentinelle.domain.pii.scan.SpaceScanCompleted;

/**
 * Infrastructure adapter for publishing scan events.
 * Maps domain events to the Spring event publishing mechanism.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanEventPublisherAdapter implements PublishEventPort {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishCompleteEvent(SpaceScanCompleted spaceScanCompleted) {
        applicationEventPublisher.publishEvent(spaceScanCompleted);
        log.debug("Published scanId={}, spaceKey={}", spaceScanCompleted.scanId(), spaceScanCompleted.spaceKey());
    }
}
