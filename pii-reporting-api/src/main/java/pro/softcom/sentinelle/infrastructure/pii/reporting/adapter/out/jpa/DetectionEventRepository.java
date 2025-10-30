package pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventEntity;
import pro.softcom.sentinelle.infrastructure.pii.reporting.adapter.out.jpa.entity.ScanEventId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DetectionEventRepository extends
    JpaRepository<@NonNull ScanEventEntity, @NonNull ScanEventId> {

    @Query("select coalesce(max(e.eventSeq),0) from ScanEventEntity e where e.scanId = :scanId")
    long findMaxEventSeqByScanId(@Param("scanId") String scanId);

    @Query("select count(distinct e.spaceKey) from ScanEventEntity e where e.scanId = :scanId")
    int countDistinctSpaceKeyByScanId(@Param("scanId") String scanId);

    interface SpaceCountersProjection {
        String getSpaceKey();
        long getPagesDone();
        long getAttachmentsDone();
        Instant getLastEventTs();
    }

    @Query("select e.spaceKey as spaceKey, " +
        "sum(case when e.eventType = 'page_complete' then 1 else 0 end) as pagesDone, " +
        "sum(case when e.eventType = 'attachment_item' then 1 else 0 end) as attachmentsDone, " +
        "max(e.ts) as lastEventTs " +
        "from ScanEventEntity e where e.scanId = :scanId group by e.spaceKey")
    List<SpaceCountersProjection> aggregateSpaceCounters(@Param("scanId") String scanId);

    interface LatestScanProjection {
        String getScanId();
        Instant getLastUpdated();
    }

    @Query("select e.scanId as scanId, max(e.ts) as lastUpdated from ScanEventEntity e group by e.scanId order by max(e.ts) desc")
    java.util.List<LatestScanProjection> findLatestScanGrouped(
        org.springframework.data.domain.Pageable pageable);

    List<ScanEventEntity> findByScanIdAndEventTypeInOrderByEventSeqAsc(String scanId, Collection<String> eventTypes);

    List<ScanEventEntity> findByScanIdAndPageIdAndEventTypeInOrderByEventSeqAsc(
            String scanId, String pageId, Collection<String> eventTypes
    );

    List<ScanEventEntity> findByScanIdAndSpaceKeyAndEventTypeInOrderByEventSeqAsc(
            String scanId, String spaceKey, Collection<String> eventTypes
    );
}
