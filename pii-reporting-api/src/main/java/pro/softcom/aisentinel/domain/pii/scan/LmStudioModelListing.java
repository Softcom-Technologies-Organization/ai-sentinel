package pro.softcom.aisentinel.domain.pii.scan;

import java.util.List;

/**
 * Ministral-PII models LM Studio has on disk, as listed by the detector service.
 *
 * @param endpoint Where the models were listed
 * @param family   Identifier prefix every listed model shares
 * @param models   The models, empty when the listing failed
 * @param error    Empty when the listing succeeded; short technical reason otherwise
 */
public record LmStudioModelListing(String endpoint, String family, List<LmStudioModel> models, String error) {

    public LmStudioModelListing {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public boolean failed() {
        return error != null && !error.isBlank();
    }
}
