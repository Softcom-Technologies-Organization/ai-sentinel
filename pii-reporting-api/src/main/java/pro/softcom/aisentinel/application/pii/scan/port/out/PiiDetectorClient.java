package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;
import pro.softcom.aisentinel.domain.pii.scan.DetectorHealth;

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

    ContentPiiDetection analyzeContent(String content) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorException;
}
