package pro.softcom.aisentinel.application.pii.scan.port.out;

import pro.softcom.aisentinel.domain.pii.scan.ContentPiiDetection;

/**
 * Output port for delegating PII detection on raw text content to an external detector.
 *
 * <p>Implementations typically wrap a gRPC or HTTP client to the PII detector service. The
 * {@code analyzePageContent} overloads enrich detections with page/space metadata useful for
 * downstream reporting, while the plain {@code analyzeContent} variants are suitable for
 * stateless content scanning (e.g. attachments).
 */
public interface PiiDetectorClient {

    /** Analyzes raw text content using the detector default confidence threshold. */
    ContentPiiDetection analyzeContent(String content) throws PiiDetectorException;

    /** Analyzes raw text content, keeping only detections above the given confidence threshold. */
    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorException;

    /** Analyzes page content and tags detections with the source page/space metadata. */
    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorException;

    /** Analyzes page content with a custom confidence threshold; detections are tagged with page metadata. */
    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorException;
}
