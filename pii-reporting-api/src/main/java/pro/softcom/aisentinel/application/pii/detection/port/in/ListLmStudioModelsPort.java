package pro.softcom.aisentinel.application.pii.detection.port.in;

import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;

/**
 * Port IN listing the Ministral-PII models available on the LM Studio endpoint,
 * so an operator can pick a quantization from the dashboard.
 */
public interface ListLmStudioModelsPort {

    /**
     * Lists the models of the configured endpoint, or of the given one when the
     * operator is editing the host and port.
     *
     * @param lmStudioHost host to query, or null/blank for the configured host
     * @param lmStudioPort port to query, or null for the configured port
     * @param allModels    true to list every chat model (LLM judge), false for the
     *                     Ministral-PII family only (detection)
     * @return the listing; never null, its error is set when LM Studio could not be listed
     */
    LmStudioModelListing listModels(String lmStudioHost, Integer lmStudioPort, boolean allModels);
}
