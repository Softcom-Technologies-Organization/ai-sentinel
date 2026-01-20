package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.scan.SpaceScanCompleted;

public interface PublishEventPort {
    void publishCompleteEvent(SpaceScanCompleted spaceScanCompleted);
}
