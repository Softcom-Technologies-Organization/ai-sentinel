package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.scan.SourceScanCompleted;

public interface PublishEventPort {
    void publishCompleteEvent(SourceScanCompleted sourceScanCompleted);
}
