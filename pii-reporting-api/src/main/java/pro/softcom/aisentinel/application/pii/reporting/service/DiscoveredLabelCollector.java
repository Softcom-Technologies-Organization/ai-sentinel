package pro.softcom.aisentinel.application.pii.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pro.softcom.aisentinel.application.pii.detection.port.out.DiscoveredLabelStore;

import java.util.Map;

/**
 * Accumulates open-vocabulary labels discovered by the MINISTRAL detector.
 *
 * <p>Business purpose: collects the labels the model proposes outside the
 * configured vocabulary so an operator can review and promote them later.
 *
 * <p>This collector is invoked from the scan flux on a background scheduler.
 * It is self-contained and fail-open: it no-ops on empty input and swallows
 * persistence failures (logged at warn) so label collection can never make the
 * scan itself fail.
 *
 * <p>Collection is gated by {@code enabled}: disabled by default so no discovered
 * labels are persisted in production. The strict type filtering that drops those
 * unconfigured labels lives in the detector service and is independent of this
 * gate; it always applies. Enable only for debugging the model's proposed
 * vocabulary.
 */
@RequiredArgsConstructor
@Slf4j
public class DiscoveredLabelCollector {

    private final DiscoveredLabelStore store;
    private final boolean enabled;

    /**
     * Records the discovered-label occurrences carried by a single detection.
     *
     * @param labelCounts UPPER_SNAKE label to per-request occurrence count
     *                    (ignored when null or empty)
     */
    public void recordOccurrences(Map<String, Integer> labelCounts) {
        if (!enabled || labelCounts == null || labelCounts.isEmpty()) {
            return;
        }
        try {
            store.recordOccurrences(labelCounts);
        } catch (Exception exception) {
            log.warn("[DISCOVERED_LABELS] Failed to record discovered labels: {}", exception.getMessage());
        }
    }
}
