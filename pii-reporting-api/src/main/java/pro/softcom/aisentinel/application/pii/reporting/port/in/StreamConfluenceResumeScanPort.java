package pro.softcom.aisentinel.application.pii.reporting.port.in;

import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;
import reactor.core.publisher.Flux;

public interface StreamConfluenceResumeScanPort {
    Flux<ScanResult> resumeAllSpaces(String scanId);
}
