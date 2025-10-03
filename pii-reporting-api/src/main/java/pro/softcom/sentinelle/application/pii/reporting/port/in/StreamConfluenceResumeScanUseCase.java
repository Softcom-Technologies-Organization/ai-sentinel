package pro.softcom.sentinelle.application.pii.reporting.port.in;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

public interface StreamConfluenceResumeScanUseCase {
    Flux<ScanResult> resumeAllSpaces(String scanId);
}
