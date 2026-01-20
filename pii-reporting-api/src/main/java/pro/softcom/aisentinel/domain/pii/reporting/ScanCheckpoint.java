package pro.softcom.aisentinel.domain.pii.reporting;

import lombok.Builder;
import pro.softcom.aisentinel.domain.pii.ScanStatus;

import java.time.LocalDateTime;

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
