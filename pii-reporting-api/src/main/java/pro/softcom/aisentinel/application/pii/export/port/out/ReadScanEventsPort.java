package pro.softcom.aisentinel.application.pii.export.port.out;

import java.util.stream.Stream;
import pro.softcom.aisentinel.domain.pii.reporting.ScanResult;

public interface ReadScanEventsPort {
    Stream<ScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey);
}
