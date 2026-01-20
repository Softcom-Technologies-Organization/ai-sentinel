package pro.softcom.aisentinel.application.pii.reporting.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.AccessPurpose;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.domain.pii.reporting.LastScanMeta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    List<ConfluenceContentScanResult> listItemEvents(String scanId);

    /**
     * Lists item events with ENCRYPTED PII data.
     * Use when PII values don't need to be viewed (statistics, dashboards without detail).
     *
     * @param scanId scan identifier
     * @return list of scan results with encrypted PII
     */
    List<ConfluenceContentScanResult> listItemEventsEncrypted(String scanId);

    /**
     * Lists item events with DECRYPTED PII data.
     * Automatically logs access for GDPR/nLPD compliance.
     *
     * @param scanId scan identifier
     * @param pageId page ID
     * @param purpose access purpose (for audit trail)
     * @return list of scan results with decrypted PII
     */
    List<ConfluenceContentScanResult> listItemEventsDecrypted(String scanId, String pageId, AccessPurpose purpose);

    /**
     * Lists item events with ENCRYPTED PII data filtered by space.
     * Use when PII values don't need to be viewed.
     *
     * @param scanId scan identifier
     * @param spaceKey Confluence space key to filter results
     * @return list of scan results with encrypted PII for the specified space
     */
    List<ConfluenceContentScanResult> listItemEventsEncryptedByScanIdAndSpaceKey(String scanId, String spaceKey);

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
