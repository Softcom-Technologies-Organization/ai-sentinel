package pro.softcom.aisentinel.infrastructure.pii.export.adapter.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.export.port.out.ReadScanEventsPort;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScanEventJpaAdapter implements ReadScanEventsPort {
    private final ScanResultQuery scanResultQuery;

    @Override
    public Stream<ConfluenceContentScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey) {
        return scanResultQuery.listItemEventsEncryptedByScanIdAndSpaceKey(scanId, spaceKey).stream();
    }
}
