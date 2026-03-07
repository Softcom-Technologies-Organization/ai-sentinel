package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pro.softcom.aisentinel.application.pii.reporting.port.out.PublishEventPort;
import pro.softcom.aisentinel.domain.pii.scan.SourceScanCompleted;

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
    public void publishCompleteEvent(SourceScanCompleted sourceScanCompleted) {
        applicationEventPublisher.publishEvent(sourceScanCompleted);
        log.debug("Published scanId={}, sourceKey={}, sourceType={}",
                sourceScanCompleted.scanId(), sourceScanCompleted.sourceKey(), sourceScanCompleted.sourceType());
    }
}
