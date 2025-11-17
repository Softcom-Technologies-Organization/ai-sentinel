package pro.softcom.sentinelle.application.pii.reporting.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.sentinelle.application.pii.reporting.port.in.PurgeDetectionDataPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanCheckpointStore;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanEventStore;

/**
 * Application use case implementing the purge of all scan-related data.
 * This class is framework-agnostic (no Spring), as per architecture rules.
 */
@Slf4j
@RequiredArgsConstructor
public class PurgeDetectionDataUseCase implements PurgeDetectionDataPort {

    private final ScanEventStore eventStore;
    private final ScanCheckpointStore checkpointStore;

    @Override
    public void purgeAll() {
        log.info("[PURGE] UseCase -> purgeAll (checkpoints then events)");
        checkpointStore.deleteAll();
        eventStore.deleteAll();
    }
}
