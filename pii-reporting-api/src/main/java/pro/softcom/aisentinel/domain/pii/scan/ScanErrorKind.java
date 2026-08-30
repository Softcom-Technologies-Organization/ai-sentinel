package pro.softcom.aisentinel.domain.pii.scan;

import java.util.Map;

/**
 * Business severity of an error raised while scanning one item.
 *
 * <p><strong>Business rule:</strong> only an outage that affects every remaining
 * item pauses the scan. A failure bound to the item being analysed — a detection
 * that outran its timeout, an attachment the detector refused — is expected to
 * happen a few times on a full scan, so pausing on it would mean no scan could
 * ever reach its end.
 *
 * <p>Pausing rather than failing is what makes the outage recoverable: the scan
 * keeps its checkpoints, so the operator fixes the cause (detector back up, VPN
 * reconnected) and the Resume button picks the scan up where it stopped.
 */
public enum ScanErrorKind {

    /** An enabled detector went down mid-scan; every further item would be analysed incompletely. */
    DETECTOR_UNAVAILABLE(ScanErrorKeys.PAUSED_DETECTOR),

    /** The data source became unreachable (network down, VPN dropped); no further item can be read. */
    NETWORK_UNAVAILABLE(ScanErrorKeys.PAUSED_NETWORK),

    /** A failure bound to this single item; the scan carries on and the item is reported as failed. */
    ITEM_FAILURE("");

    private final String pauseErrorKey;

    ScanErrorKind(String pauseErrorKey) {
        this.pauseErrorKey = pauseErrorKey;
    }

    /**
     * Whether encountering this error must pause the whole scan.
     *
     * @return true when the cause affects every remaining item
     */
    public boolean pausesScan() {
        return !pauseErrorKey.isEmpty();
    }

    /**
     * Builds the error the dashboard shows for a scan this kind paused.
     *
     * <p>The key tells which outage struck, so the dashboard can say it in the
     * operator's language and offer the Resume button; the cause rides along as a
     * technical parameter, untranslated.
     *
     * @param cause short technical reason, as reported by the failing call
     * @return the translatable error, or null for a kind that does not pause the scan
     */
    public TranslatableError toPauseError(String cause) {
        if (!pausesScan()) {
            return null;
        }
        return new TranslatableError(pauseErrorKey, Map.of("cause", cause == null ? "" : cause));
    }
}
