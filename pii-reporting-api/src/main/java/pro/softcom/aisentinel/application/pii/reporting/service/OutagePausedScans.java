package pro.softcom.aisentinel.application.pii.reporting.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans an outage paused, and that no resume has picked up since.
 *
 * <p>Business purpose: a scan longer than the VPN session could never finish
 * unattended. The session ends, Confluence becomes unreachable, the scan pauses —
 * and until a human pressed Resume, nothing moved. Recording the scan here is what
 * lets the scheduled resume pick it up on its own once the outage clears.
 *
 * <p>A pause the operator asked for is never recorded: it must stay paused until
 * they decide otherwise.
 */
public final class OutagePausedScans {

    private final Set<String> scanIds = ConcurrentHashMap.newKeySet();

    /**
     * Records a scan an outage just paused.
     *
     * @param scanId the business identifier of the scan; blank ids are ignored
     */
    public void recordOutagePause(String scanId) {
        if (scanId != null && !scanId.isBlank()) {
            scanIds.add(scanId);
        }
    }

    /**
     * Drops a scan from the waiting list because a resume has begun for it.
     *
     * <p>Called by the resume itself rather than by whoever asked for it, so a resume
     * the operator started also takes the scan out of the automatic list. A resume
     * that runs straight back into the outage is recorded anew by the pause it causes,
     * so nothing is lost by forgetting early.
     *
     * @param scanId the business identifier of the scan
     */
    public void forget(String scanId) {
        scanIds.remove(scanId);
    }

    /**
     * Lists the scans waiting for their outage to clear.
     *
     * @return a snapshot, safe to iterate while other threads record or forget
     */
    public Set<String> snapshot() {
        return Set.copyOf(scanIds);
    }
}
