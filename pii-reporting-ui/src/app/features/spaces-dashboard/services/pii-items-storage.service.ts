import {inject, Injectable, signal} from '@angular/core';
import {ItemsBySpace} from '../../../core/models/item-by-space';
import {
  PersonallyIdentifiableInformationScanResult
} from '../../../core/models/personally-identifiable-information-scan-result';
import {RawStreamPayload} from '../../../core/models/stream-event-type';
import {Severity} from '../../../core/models/severity';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';

/**
 * Service responsible for managing PII items storage and aggregation.
 *
 * Business purpose:
 * - Stores detected PII items per space (max 400 items per space)
 * - Prevents duplicate items (by pageId + attachmentName)
 * - Computes severity counts (high/medium/low) per space
 * - Provides reactive access to items and counts
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles PII items storage and counting
 * - Open/Closed: Can be extended with new aggregation logic
 * - Dependency Inversion: Depends on abstractions (SentinelleApiService, SpacesDashboardUtils)
 *
 * Business Rules:
 * - Max 400 items kept per space (FIFO when limit exceeded)
 * - Items without entities are skipped (no empty cards)
 * - Deduplication by pageId + attachmentName combination
 * - Counts computed from entities' severity (high > medium > low)
 */
@Injectable({
  providedIn: 'root'
})
export class PiiItemsStorageService {
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  // In-memory storage of PII items per space
  readonly itemsBySpace = signal<ItemsBySpace>({});

  /**
   * Adds a PII item to the in-memory store for a space.
   *
   * Business rules:
   * - Skips items without entities (no empty cards)
   * - Deduplicates by pageId + attachmentName
   * - Keeps max 400 items per space (FIFO)
   *
   * @returns true if item was added (not a duplicate), false otherwise
   */
  addPiiItemToSpace(spaceKey: string, payload: RawStreamPayload): boolean {
    const entities = Array.isArray(payload.detectedPersonallyIdentifiableInformationList) ? payload.detectedPersonallyIdentifiableInformationList : [];

    // Skip creating a card when no PII entities were detected
    if (!entities.length) {
      return false;
    }

    // Use backend-provided severity, normalize to lowercase for frontend compatibility
    const backendSeverity = payload.severity?.toLowerCase() as Severity | undefined;
    const severity: Severity = backendSeverity && ['high', 'medium', 'low'].includes(backendSeverity)
      ? backendSeverity
      : 'low';  // Fallback for legacy events without severity

    const piiItem: PersonallyIdentifiableInformationScanResult = {
      scanId: payload.scanId ?? '',
      spaceKey: spaceKey,
      pageId: String(payload.pageId ?? ''),
      pageTitle: payload.pageTitle,
      pageUrl: payload.pageUrl,
      emittedAt: payload.emittedAt,
      isFinal: !!payload.isFinal,
      severity,
      summary: (payload.summary && typeof payload.summary === 'object') ? payload.summary : undefined,
      detectedPersonallyIdentifiableInformationList: entities.map((e: any) => {
        return {
          startPosition: e?.startPosition,
          endPosition: e?.endPosition,
          piiTypeLabel: e?.piiTypeLabel,
          piiType: e?.piiType,
          sensitiveValue: e?.sensitiveValue,
          sensitiveContext: e?.sensitiveContext,
          maskedContext: e?.maskedContext,
          confidence: typeof e?.confidence === 'number' ? e.confidence : undefined
        };
      }),
      attachmentName: payload.attachmentName,
      attachmentType: payload.attachmentType,
      attachmentUrl: payload.attachmentUrl
    };

    const previous = this.itemsBySpace()[spaceKey] ?? [];

    // Deduplicate: skip if an item for the same page/attachment already exists
    const isDuplicate = previous.some(
      it => it.pageId === piiItem.pageId && it.attachmentName === piiItem.attachmentName
    );

    if (isDuplicate) {
      return false;
    }

    // Add new item at the beginning (most recent first)
    const nextItems = [piiItem, ...previous];

    // Keep max 400 items per space
    if (nextItems.length > 400) {
      nextItems.length = 400;
    }

    this.itemsBySpace.set({ ...this.itemsBySpace(), [spaceKey]: nextItems });
    return true;
  }


  /**
   * Clears all items for all spaces.
   * Business purpose: Reset dashboard for new scan.
   */
  clearAllItems(): void {
    const map = this.itemsBySpace();
    this.itemsBySpace.set({});

    // Reset counts for all spaces that previously had entries
    for (const key of Object.keys(map)) {
      this.spacesDashboardUtils.updateSpace(key, {
        counts: { total: 0, high: 0, medium: 0, low: 0 }
      });
    }
  }
}
