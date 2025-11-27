package pro.softcom.aisentinel.application.pii.export.port.out;

import java.util.stream.Stream;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

public interface ReadScanEventsPort {
    Stream<ConfluenceContentScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey);
}
