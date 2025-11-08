package pro.softcom.sentinelle.application.pii.export.port.out;

import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;

import java.util.stream.Stream;

public interface ReadScanEventsPort {
    Stream<ScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey);
}
