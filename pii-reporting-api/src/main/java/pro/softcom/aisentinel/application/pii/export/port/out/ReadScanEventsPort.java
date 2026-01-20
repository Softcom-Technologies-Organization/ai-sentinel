package pro.softcom.aisentinel.application.pii.export.port.out;

import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;

import java.util.stream.Stream;

public interface ReadScanEventsPort {
    Stream<ConfluenceContentScanResult> streamByScanIdAndSpaceKey(String scanId, String spaceKey);
}
