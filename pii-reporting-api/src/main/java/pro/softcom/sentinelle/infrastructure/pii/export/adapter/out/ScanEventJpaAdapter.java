package pro.softcom.sentinelle.infrastructure.pii.export.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScanEventJpaAdapter implements ReadScanEventsPort {
    private final ScanResultQuery scanResultQuery;

    @Override
    public Stream<ScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey) {
        return scanResultQuery.listItemEventsByScanIdAndSpaceKey(scanId, spaceKey).stream();
    }
}
