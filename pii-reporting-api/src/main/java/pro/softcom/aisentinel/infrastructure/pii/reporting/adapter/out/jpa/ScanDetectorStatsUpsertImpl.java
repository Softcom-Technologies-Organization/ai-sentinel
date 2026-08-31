package pro.softcom.aisentinel.infrastructure.pii.reporting.adapter.out.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import pro.softcom.aisentinel.domain.pii.reporting.ScanDetectorStatDelta;

class ScanDetectorStatsUpsertImpl implements ScanDetectorStatsUpsert {

    private static final String UPSERT = """
        INSERT INTO scan_detector_stats (scan_id, space_key, detector, busy_ms, chars_processed, detections, discarded, failed_requests, last_error, updated_at)
        VALUES (:scanId, :spaceKey, :detector, :busyMs, :chars, :detections, :discarded, :failedRequests, NULLIF(:lastError, ''), now())
        ON CONFLICT (scan_id, space_key, detector) DO UPDATE
        SET busy_ms = scan_detector_stats.busy_ms + :busyMs,
            chars_processed = scan_detector_stats.chars_processed + :chars,
            detections = scan_detector_stats.detections + :detections,
            discarded = scan_detector_stats.discarded + :discarded,
            failed_requests = scan_detector_stats.failed_requests + :failedRequests,
            last_error = COALESCE(NULLIF(:lastError, ''), scan_detector_stats.last_error),
            updated_at = now()
        """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void accumulate(String scanId, String spaceKey, ScanDetectorStatDelta delta) {
        entityManager.createNativeQuery(UPSERT)
            .setParameter("scanId", scanId)
            .setParameter("spaceKey", spaceKey)
            .setParameter("detector", delta.detector())
            .setParameter("busyMs", delta.busyMs())
            .setParameter("chars", delta.chars())
            .setParameter("detections", delta.detections())
            .setParameter("discarded", delta.discarded())
            .setParameter("failedRequests", delta.failedRequests())
            .setParameter("lastError", delta.lastError())
            .executeUpdate();

        // Same intent as the former @Modifying(clearAutomatically = true): the UPSERT
        // bypasses the persistence context, so entities loaded before it are stale.
        entityManager.clear();
    }
}
