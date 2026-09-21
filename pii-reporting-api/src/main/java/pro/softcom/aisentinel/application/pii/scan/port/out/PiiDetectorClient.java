package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeAnswer;
import pro.softcom.aisentinel.domain.pii.judge.FindingJudgeRequest;
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
     * Lists the models LM Studio has on disk, as seen from the detection service. A blank
     * host or a null port means "the configured endpoint".
     *
     * @param lmStudioHost LM Studio host to query, or null/blank for the configured one
     * @param lmStudioPort LM Studio port to query, or null for the configured one
     * @param allModels    true to list every chat model (LLM judge), false for the
     *                     Ministral-PII family only (detection)
     * @return the listing; its error is set when the endpoint could not be listed
     * @throws PiiDetectorException when the detection service itself cannot be reached
     */
    LmStudioModelListing listLmStudioModels(String lmStudioHost, Integer lmStudioPort, boolean allModels)
            throws PiiDetectorException;

    /**
     * Asks the LLM judge, through the detection service, whether one finding really is an
     * instance of its PII type in its context.
     *
     * @return the raw answer; its error is set when the judge could not answer
     * @throws PiiDetectorException when the detection service itself cannot be reached
     */
    FindingJudgeAnswer judgeFinding(FindingJudgeRequest request) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorException;
}
