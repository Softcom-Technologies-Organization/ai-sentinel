package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.scan.SpaceScanCompleted;

/**
 * Output port for publishing domain events emitted by the scan application layer.
 *
 * <p>Decouples the application layer from any concrete messaging/event infrastructure (internal
 * bus, Spring events, Kafka, etc.).
 */
public interface PublishEventPort {

    /** Publishes the terminal event signalling that a space scan has completed. */
    void publishCompleteEvent(SpaceScanCompleted spaceScanCompleted);
}
