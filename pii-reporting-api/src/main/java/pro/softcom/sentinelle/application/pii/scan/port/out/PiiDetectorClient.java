package pro.softcom.sentinelle.application.pii.scan.port.out;

import pro.softcom.sentinelle.domain.pii.scan.ContentPiiDetection;

public interface PiiDetectorClient {

    ContentPiiDetection analyzeContent(String content) throws PiiDetectorException;

    ContentPiiDetection analyzeContent(String content, float threshold) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content) throws PiiDetectorException;

    ContentPiiDetection analyzePageContent(String pageId, String pageTitle, String spaceKey, String content, float threshold) throws PiiDetectorException;
}
