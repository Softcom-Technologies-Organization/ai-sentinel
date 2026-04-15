package pro.softcom.aisentinel.application.pii.export.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.util.stream.Stream;

/**
 * Output port for replaying persisted scan events (e.g. during report export).
 *
 * <p>Returns a lazy {@link Stream} so consumers can process large scans without loading the full
 * event history into memory. Callers are responsible for closing the stream.
 */
public interface ReadScanEventsPort {

    /**
     * Streams scan events for the given scan identifier and space key in persistence order.
     *
     * @param scanId   unique identifier of the scan
     * @param spaceKey Confluence space key, used to filter events
     * @return a lazy stream of scan results; must be closed by the caller
     */
    Stream<ConfluenceContentScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey);
}
