package pro.softcom.aisentinel.infrastructure.pii.export.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.export.port.in.ExportDetectionReportPort;
import pro.softcom.aisentinel.domain.pii.scan.SourceScanCompleted;

/**
 * Listens for source scan completion events and triggers the export of detection reports.
 * This adapter connects the event-driven architecture to the export use case,
 * using the source type from the event to determine the correct export strategy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SourceScanCompletedListener {

    private final ExportDetectionReportPort exportDetectionReportPort;

    @EventListener
    public void onSourceScanCompleted(SourceScanCompleted event) {
        if (event == null) {
            log.warn("Received a null event");
            return;
        }

        log.info("Received SourceScanCompleted: scanId={}, sourceKey={}, sourceType={}",
                event.scanId(), event.sourceKey(), event.sourceType());

        try {
            exportDetectionReportPort.export(event.scanId(), event.sourceType(), event.sourceKey());
        } catch (Exception ex) {
            log.error("Failed to export for scanId={}, sourceKey={}: {}",
                    event.scanId(), event.sourceKey(), ex.getMessage(), ex);
        }
    }
}
