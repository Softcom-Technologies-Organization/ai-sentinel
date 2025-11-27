package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pro.softcom.aisentinel.application.pii.reporting.port.out.ScanEventStore;
import pro.softcom.aisentinel.application.pii.reporting.service.PiiContextExtractor;
import pro.softcom.aisentinel.application.pii.security.ScanResultEncryptor;
import pro.softcom.aisentinel.domain.pii.reporting.ConfluenceContentScanResult;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

/**
 * Event store service that persists every emitted ScanEvent into PostgreSQL (scan_events JSONB).
 * Business intent: enable reconstruction of the last scan results even if streaming was interrupted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaScanEventStoreAdapter implements ScanEventStore {

    private final DetectionEventRepository eventRepository;
    private final ScanResultEncryptor scanResultEncryptor;
    private final PiiContextExtractor piiContextExtractor;
    private final ObjectMapper objectMapper;

    // In-memory per-scan sequence cache, initialized lazily from DB on first use
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    /**
     * Append an event to the event store. Best-effort: logs on failure but does not throw.
     */
    @Override
    public void append(ConfluenceContentScanResult confluenceContentScanResult) {
        try {
            Objects.requireNonNull(confluenceContentScanResult, "scanResult cannot be null");
            if (StringUtils.isBlank(confluenceContentScanResult.scanId())) {
                throw new IllegalArgumentException("scanId cannot be blank");
            }
            if (StringUtils.isBlank(confluenceContentScanResult.eventType())) {
                throw new IllegalArgumentException("eventType cannot be blank");
            }

            ConfluenceContentScanResult encryptedResult = scanResultEncryptor.encrypt(
                confluenceContentScanResult);
            JsonNode payload = objectMapper.valueToTree(encryptedResult);

            String scanId = confluenceContentScanResult.scanId();
            long seq = nextSeq(scanId);
            Instant scanRecordedAt = parseInstant(confluenceContentScanResult.emittedAt());

            ScanEventEntity entity = ScanEventEntity.builder()
                    .scanId(scanId)
                    .eventSeq(seq)
                    .spaceKey(confluenceContentScanResult.spaceKey())
                    .eventType(confluenceContentScanResult.eventType())
                    .ts(scanRecordedAt != null ? scanRecordedAt : Instant.now())
                    .pageId(confluenceContentScanResult.pageId())
                    .pageTitle(confluenceContentScanResult.pageTitle())
                    .attachmentName(confluenceContentScanResult.attachmentName())
                    .attachmentType(confluenceContentScanResult.attachmentType())
                    .payload(payload)
                    .build();

            eventRepository.save(entity);
        } catch (IllegalArgumentException e) {
            log.warn("[EVENT_STORE] Invalid scan result: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[EVENT_STORE] Unable to append event: {}", e.getMessage());
        }
    }

    private long nextSeq(String scanId) {
        Objects.requireNonNull(scanId, "scanId");
        AtomicLong counter = sequences.computeIfAbsent(scanId, this::initCounterFromDb);
        return counter.incrementAndGet();
    }

    private AtomicLong initCounterFromDb(String scanId) {
        try {
            long last = eventRepository.findMaxEventSeqByScanId(scanId);
            return new AtomicLong(last);
        } catch (Exception ex) {
            log.warn("[EVENT_STORE] Failed to read last event_seq for {}: {}", scanId, ex.getMessage());
            return new AtomicLong(0);
        }
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception _) {
            return null;
        }
    }

    @Override
    public void deleteAll() {
        eventRepository.deleteAllInBatch();
    }

}
