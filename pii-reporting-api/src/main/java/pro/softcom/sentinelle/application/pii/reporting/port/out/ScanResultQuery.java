package pro.softcom.sentinelle.application.pii.reporting.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

/**
 * Application out port used by the reporting layer to read scan outcomes.
 */
public interface ScanResultQuery {

    /**
     * Returns the most recent scan known by the system, if any.
     *
     * @return an Optional containing the metadata of the latest scan, or empty when no scan exists
     */
    Optional<LastScanMeta> findLatestScan();

    /**
     * Returns progress counters per space for the given scan.
     * The counters reflect how many pages and attachments have been processed,
     * along with the timestamp of the last observed event for that space.
     *
     * @param scanId the business identifier of the scan to inspect
     * @return a list of per-space counters for the requested scan (may be empty)
     */
    List<SpaceCounter> getSpaceCounters(String scanId);

    /**
     * Lists the item events for the given scan in emission order.
     * An item represents a content unit such as a page or an attachment.
     *
     * @param scanId the business identifier of the scan to inspect
     * @return the ordered list of item events recorded for the scan (may be empty)
     */
    List<ScanResult> listItemEvents(String scanId);

    /**
     * Read-side projection representing per-space progress within a scan.
     *
     * @param spaceKey the business key of the space
     * @param pagesDone number of pages processed in this space
     * @param attachmentsDone number of attachments processed in this space
     * @param lastEventTs timestamp of the last event observed for this space
     */
    record SpaceCounter(String spaceKey, long pagesDone, long attachmentsDone, Instant lastEventTs) {}
}
