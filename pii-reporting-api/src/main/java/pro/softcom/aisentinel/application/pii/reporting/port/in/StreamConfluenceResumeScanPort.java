package pro.softcom.aisentinel.application.pii.reporting.port.in;

import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import reactor.core.publisher.Flux;

public interface StreamConfluenceResumeScanPort {
    Flux<ConfluenceContentScanResult> resumeAllSpaces(String scanId);
}
