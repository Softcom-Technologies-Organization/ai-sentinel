package pro.softcom.aisentinel.domain.pii.scan;

/**
 * Translation keys for every scan error the dashboard can be asked to display.
 *
 * <p>Kept in one place because each key here must have a French and an English
 * wording in the frontend translation files — {@code pii-reporting-ui/src/assets/i18n/fr.json}
 * and {@code en.json}, under {@code errors.scan} — with a {@code title} and a
 * {@code detail} entry. Adding a key without translating it leaves the operator
 * facing a raw key, so the two sides are meant to be read side by side.
 */
public final class ScanErrorKeys {

    /** An enabled detector was never built, so it silently contributes nothing. */
    public static final String DETECTOR_NOT_INSTANTIATED = "error.scan.detector_not_instantiated";

    /** The detector is on in the database but off in its own model configuration. */
    public static final String DETECTOR_DISABLED_IN_CONFIG = "error.scan.detector_disabled_in_config";

    /** The detector failed to initialise, so it would find nothing. */
    public static final String DETECTOR_INIT_FAILED = "error.scan.detector_init_failed";

    /** The remote detector endpoint did not answer the probe. */
    public static final String DETECTOR_ENDPOINT_UNREACHABLE = "error.scan.detector_endpoint_unreachable";

    /** The endpoint answered, but does not serve the configured model. */
    public static final String DETECTOR_MODEL_NOT_AVAILABLE = "error.scan.detector_model_not_available";

    /** The model is on the endpoint but not loaded, so every request would fail. */
    public static final String DETECTOR_MODEL_NOT_LOADED = "error.scan.detector_model_not_loaded";

    /** Fallback for a detection service reporting a reason this version does not know. */
    public static final String DETECTOR_UNUSABLE = "error.scan.detector_unusable";

    /** An enabled detector went down mid-scan; the scan is paused and resumable. */
    public static final String PAUSED_DETECTOR = "error.scan.paused_detector";

    /** The data source became unreachable mid-scan; the scan is paused and resumable. */
    public static final String PAUSED_NETWORK = "error.scan.paused_network";

    /** Analysing one page outran the reactor timeout; the scan carries on. */
    public static final String PAGE_TIMEOUT = "error.scan.page_timeout";

    /** Analysing one attachment outran the reactor timeout; the scan carries on. */
    public static final String ATTACHMENT_TIMEOUT = "error.scan.attachment_timeout";

    /** The detection service outran its own deadline on one item. */
    public static final String DETECTION_TIMEOUT = "error.scan.detection_timeout";

    /** The detection service refused one item. */
    public static final String DETECTION_FAILED = "error.scan.detection_failed";

    /** One page could not be analysed for a reason bound to that page. */
    public static final String PAGE_FAILED = "error.scan.page_failed";

    /** One attachment could not be analysed for a reason bound to that attachment. */
    public static final String ATTACHMENT_FAILED = "error.scan.attachment_failed";

    /** The requested space does not exist in the data source. */
    public static final String SPACE_NOT_FOUND = "error.scan.space_not_found";

    /** The data source exposes no space to scan. */
    public static final String NO_SPACE_FOUND = "error.scan.no_space_found";

    /** Anything the scan did not foresee; the technical cause is carried as a parameter. */
    public static final String UNEXPECTED = "error.scan.unexpected";

    private ScanErrorKeys() {
        // constants holder
    }
}
