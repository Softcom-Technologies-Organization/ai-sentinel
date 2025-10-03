package pro.softcom.sentinelle.application.pii.scan.port.out;

import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;

/**
 * Port sortant pour la détection de PII.
 */
public interface PiiDetectorClient {

    ContentPiiDetection analyzeContent(String content) throws PiiDetectorError;

    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorError;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorError;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorError;
}
