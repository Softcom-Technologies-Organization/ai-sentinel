package pro.softcom.sentinelle.application.pii.reporting.port.out;

import pro.softcom.sentinelle.domain.pii.scan.SpaceScanCompleted;

public interface PublishEventPort {
    void publishCompleteEvent(SpaceScanCompleted spaceScanCompleted);
}
