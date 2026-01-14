package pro.softcom.aisentinel.application.pii.reporting.port.in;

import java.util.List;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import reactor.core.publisher.Flux;

/**
 * Use case orchestrating Confluence scans and PII detection.
 */
public interface StreamConfluenceScanPort {
    Flux<ConfluenceContentScanResult> streamSpace(String spaceKey);
    Flux<ConfluenceContentScanResult> streamAllSpaces();
    Flux<ConfluenceContentScanResult> streamSelectedSpaces(List<String> spaceKeys);
}