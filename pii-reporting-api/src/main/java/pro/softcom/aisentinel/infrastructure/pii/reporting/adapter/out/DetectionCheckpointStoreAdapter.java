package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanCheckpointStore;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionCheckpointRepository;

/**
 * JPA adapter implementing the ScanCheckpointAdminStore out-port.
 */
@Component
@RequiredArgsConstructor
public class DetectionCheckpointStoreAdapter implements ScanCheckpointStore {

    private final DetectionCheckpointRepository repository;

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }
}
