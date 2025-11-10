package pro.softcom.sentinelle.application.pii.reporting.port.in;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

/**
 * Use case orchestrating Confluence scans and PII detection.
 */
public interface StreamConfluenceScanUseCase {
    Flux<ScanResult> streamSpace(String spaceKey);
    Flux<ScanResult> streamAllSpaces();
}
