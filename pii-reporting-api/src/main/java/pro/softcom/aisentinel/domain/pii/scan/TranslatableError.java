package pro.softcom.aisentinel.domain.pii.scan;

import java.util.Map;

/**
 * An error described in a form the dashboard can render in the operator's language.
 *
 * <p><strong>Business rule:</strong> no error wording built by the backend is ever
 * read by an operator. The backend names <em>which</em> error occurred through
 * {@code key}, and the dashboard owns the sentence — in French or English,
 * depending on who is looking at it.
 *
 * @param key    translation key, e.g. {@code error.scan.detector_model_not_loaded}
 * @param params values the translated sentence interpolates. Technical by nature
 *               (an endpoint, a model id, a gRPC status) and left untranslated.
 */
public record TranslatableError(String key, Map<String, String> params) {

    public TranslatableError {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /**
     * A translatable error carrying no value to interpolate.
     *
     * @param key the translation key
     * @return the error, with empty parameters
     */
    public static TranslatableError of(String key) {
        return new TranslatableError(key, Map.of());
    }
}
