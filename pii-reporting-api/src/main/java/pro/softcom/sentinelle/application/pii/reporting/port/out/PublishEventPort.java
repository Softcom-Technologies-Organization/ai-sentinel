package pro.softcom.sentinelle.application.pii.reporting.port.out;

import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.event.SpaceScanCompleted;

public interface PublishEventPort {
    void publishCompleteEvent(SpaceScanCompleted spaceScanCompleted);
}