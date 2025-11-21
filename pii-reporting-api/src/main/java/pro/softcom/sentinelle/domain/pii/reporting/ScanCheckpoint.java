package pro.softcom.sentinelle.domain.pii.reporting;

import java.time.LocalDateTime;
import lombok.Builder;
import pro.softcom.sentinelle.domain.pii.ScanStatus;

/**
 * Fine-grained checkpoint to resume scanning inside a space at page/attachment level.
 * Business goal: allow resuming a scan after interruption without reprocessing already analyzed items.
 */
@Builder
public record ScanCheckpoint(
    String scanId,
    String spaceKey,
    String lastProcessedPageId,
    String lastProcessedAttachmentName,
    ScanStatus scanStatus,
    Double progressPercentage,
    LocalDateTime updatedAt
) { }
