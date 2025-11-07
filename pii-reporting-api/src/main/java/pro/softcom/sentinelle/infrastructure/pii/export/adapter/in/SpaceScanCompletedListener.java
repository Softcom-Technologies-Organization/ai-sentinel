package pro.softcom.sentinelle.infrastructure.pii.export.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.sentinelle.domain.pii.export.SourceType;
import pro.softcom.sentinelle.domain.pii.scan.SpaceScanCompleted;

/**
 * Listens for space scan completion events and triggers the export of detection reports.
 * This adapter connects the event-driven architecture to the export use case.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpaceScanCompletedListener {

    private final ExportDetectionReportPort exportDetectionReportPort;

    @EventListener
    public void onSpaceScanCompleted(SpaceScanCompleted spaceScanCompleted) {
        if (spaceScanCompleted == null) {
            log.warn("Received a null event");
            return;
        }

        log.info("Received SpaceScanCompleted: scanId={}, spaceKey={}",
                spaceScanCompleted.scanId(), spaceScanCompleted.spaceKey());

        try {
            exportDetectionReportPort.export(
                    spaceScanCompleted.scanId(),
                    SourceType.CONFLUENCE,
                    spaceScanCompleted.spaceKey()
            );
        } catch (Exception ex) {
            log.error("Failed to export for scanId={}, spaceKey={}: {}",
                    spaceScanCompleted.scanId(), spaceScanCompleted.spaceKey(), ex.getMessage(), ex);
        }
    }
}
