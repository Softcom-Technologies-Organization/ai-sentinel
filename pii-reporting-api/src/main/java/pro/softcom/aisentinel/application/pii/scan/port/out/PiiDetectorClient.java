package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;
import pro.softcom.aisentinel.domain.pii.scan.LmStudioModelListing;

import java.util.List;

public interface PiiDetectorClient {

    /**
     * Reports whether each currently-enabled detector can actually run.
     *
     * <p>Used as a pre-flight before starting a scan, so a detector that is
     * enabled but unreachable is reported instead of silently contributing
     * nothing.
     *
     * @return one entry per enabled detector, empty when readiness is unknown
     */
    List<DetectorHealth> checkDetectorsHealth() throws PiiDetectorException;

    /**
     * Lists the Ministral-PII models LM Studio has on disk, as seen from the
     * detection service. A blank host or a null port means "the configured endpoint".
     *
     * @param lmStudioHost LM Studio host to query, or null/blank for the configured one
     * @param lmStudioPort LM Studio port to query, or null for the configured one
     * @return the listing; its error is set when the endpoint could not be listed
     * @throws PiiDetectorException when the detection service itself cannot be reached
     */
    LmStudioModelListing listLmStudioModels(String lmStudioHost, Integer lmStudioPort) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorException;
}
