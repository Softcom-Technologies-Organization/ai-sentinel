package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import pro.softcom.sentinelle.application.pii.reporting.port.out.ScanResultQuery;
import pro.softcom.sentinelle.application.pii.security.ScanResultEncryptor;
import pro.softcom.sentinelle.domain.pii.reporting.LastScanMeta;
import pro.softcom.sentinelle.domain.pii.reporting.ScanResult;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.DetectionEventRepository;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;

/**
 * Adapter JPA implémentant le port de lecture ScanResultQuery.
 * Mappe les entités/projections JPA vers des modèles du domaine.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaScanResultQueryAdapter implements ScanResultQuery {

    private final DetectionEventRepository eventRepository;
    private final ScanResultEncryptor scanResultEncryptor;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<LastScanMeta> findLatestScan() {
        var rows = eventRepository.findLatestScanGrouped(PageRequest.of(0, 1));
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        var row = rows.get(0);
        String scanId = row.getScanId();
        int spaces = eventRepository.countDistinctSpaceKeyByScanId(scanId);
        return Optional.of(new LastScanMeta(scanId, row.getLastUpdated(), spaces));
    }

    @Override
    public List<SpaceCounter> getSpaceCounters(String scanId) {
        if (scanId == null || scanId.isBlank()) {
            return List.of();
        }
        return eventRepository.aggregateSpaceCounters(scanId).stream()
            .map(p -> new SpaceCounter(p.getSpaceKey(), p.getPagesDone(), p.getAttachmentsDone(), p.getLastEventTs()))
            .toList();
    }

    @Override
    public List<ScanResult> listItemEvents(String scanId) {
        if (scanId == null || scanId.isBlank()) return List.of();
        var types = Set.of("item", "attachmentItem");
        return eventRepository.findByScanIdAndEventTypeInOrderByEventSeqAsc(scanId, types).stream()
            .map(this::toDomain)
            .filter(Objects::nonNull)
            .toList();
    }

    private ScanResult toDomain(ScanEventEntity scanEventEntity) {
        if (scanEventEntity == null || scanEventEntity.getPayload() == null) {
            log.warn("scanEventEntity or payload is null");
            return null;
        }

        try {
            ScanResult encryptedResult = objectMapper.treeToValue(scanEventEntity.getPayload(), ScanResult.class);
            return scanResultEncryptor.decrypt(encryptedResult);
        } catch (Exception e) {
            log.error("Failed to deserialize scan event", e);
            return null;
        }
    }
}
