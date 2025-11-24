package pro.softcom.aisentinel.application.pii.reporting.port.in;

import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

/**
 * Use case orchestrating Confluence scans and PII detection.
 */
public interface StreamConfluenceScanPort {
    Flux<ScanResult> streamSpace(String spaceKey);
    Flux<ScanResult> streamAllSpaces();
}
