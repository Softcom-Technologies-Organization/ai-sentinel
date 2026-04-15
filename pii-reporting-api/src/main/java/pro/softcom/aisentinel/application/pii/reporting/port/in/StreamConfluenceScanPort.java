package pro.softcom.aisentinel.application.pii.reporting.port.in;

import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Input port orchestrating Confluence scans and PII detection as reactive streams.
 *
 * <p>Each operation returns a {@link Flux} that emits a {@link ConfluenceContentScanResult} per
 * analyzed page/attachment, allowing downstream SSE delivery without buffering the full scan.
 */
public interface StreamConfluenceScanPort {

    /** Streams PII detection results for every page/attachment in the given space. */
    Flux<ConfluenceContentScanResult> streamSpace(String spaceKey);

    /** Streams PII detection results across every accessible Confluence space. */
    Flux<ConfluenceContentScanResult> streamAllSpaces();

    /** Streams PII detection results restricted to the given space keys. */
    Flux<ConfluenceContentScanResult> streamSelectedSpaces(List<String> spaceKeys);
}