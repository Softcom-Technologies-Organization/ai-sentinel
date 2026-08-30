package pro.softcom.aisentinel.domain.pii.scan;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection.DetectorSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Readiness of a single enabled detector, as reported by the detection service.
 *
 * <p>Business purpose: a detector an operator switched on but whose backing
 * endpoint is down contributes no findings, which reads exactly like a clean
 * document. Surfacing readiness before a scan starts prevents shipping a
 * silently incomplete report.
 *
 * @param source      the detector these health facts belong to
 * @param reachable   whether the detector is ready to run
 * @param endpoint    where the detector was probed, for a diagnosable message
 *                    (empty for in-process detectors)
 * @param error       short technical reason it is not reachable, empty when reachable.
 *                    A diagnostic detail for the logs — the operator reads the
 *                    translated wording {@link #failure()} points to instead.
 * @param errorCode   machine-readable reason reported by the detection service,
 *                    e.g. {@code MODEL_NOT_LOADED}; empty when reachable
 * @param errorParams values describing that reason (model id, endpoint state...)
 */
public record DetectorHealth(
    DetectorSource source,
    boolean reachable,
    String endpoint,
    String error,
    String errorCode,
    Map<String, String> errorParams
) {

    public DetectorHealth {
        errorParams = errorParams == null ? Map.of() : Map.copyOf(errorParams);
    }

    /**
     * Description of why this detector cannot be used, for the service logs.
     *
     * @return a message naming the detector, the probed endpoint and the cause
     */
    public String describeFailure() {
        String location = endpoint == null || endpoint.isBlank() ? "" : " at " + endpoint;
        String cause = error == null || error.isBlank() ? "no reason reported" : error;
        return "Detector %s is enabled but unreachable%s: %s".formatted(source, location, cause);
    }

    /**
     * The same failure, in the form the dashboard renders in the operator's language.
     *
     * <p>An unknown code falls back to a generic wording rather than a missing
     * translation: a detection service newer than this backend must still produce
     * a readable refusal, with its raw reason carried as a parameter.
     *
     * @return the translation key naming this failure, and the values it interpolates
     */
    public TranslatableError failure() {
        Map<String, String> params = new HashMap<>(errorParams);
        params.put("detector", source == null ? "" : source.name());
        params.put("endpoint", endpoint == null ? "" : endpoint);
        String key = switch (errorCode == null ? "" : errorCode) {
            case "DETECTOR_NOT_INSTANTIATED" -> ScanErrorKeys.DETECTOR_NOT_INSTANTIATED;
            case "DETECTOR_DISABLED_IN_CONFIG" -> ScanErrorKeys.DETECTOR_DISABLED_IN_CONFIG;
            case "DETECTOR_INIT_FAILED" -> ScanErrorKeys.DETECTOR_INIT_FAILED;
            case "ENDPOINT_UNREACHABLE" -> ScanErrorKeys.DETECTOR_ENDPOINT_UNREACHABLE;
            case "MODEL_NOT_AVAILABLE" -> ScanErrorKeys.DETECTOR_MODEL_NOT_AVAILABLE;
            case "MODEL_NOT_LOADED" -> ScanErrorKeys.DETECTOR_MODEL_NOT_LOADED;
            default -> ScanErrorKeys.DETECTOR_UNUSABLE;
        };
        if (ScanErrorKeys.DETECTOR_UNUSABLE.equals(key)) {
            params.put("cause", error == null || error.isBlank() ? "" : error);
        }
        return new TranslatableError(key, params);
    }
}
