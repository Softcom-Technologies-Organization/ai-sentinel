package pro.softcom.sentinelle.application.pii.reporting.port.in;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

/**
 * Cas d'usage: scanner Confluence et produire un flux de résultats.
 */
public interface StreamConfluenceScanUseCase {
    Flux<ScanResult> streamSpace(String spaceKey);
    Flux<ScanResult> streamAllSpaces();
}
