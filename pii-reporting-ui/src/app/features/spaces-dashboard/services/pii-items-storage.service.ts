import {computed, inject, Injectable, signal} from '@angular/core';
import {ItemsBySpace} from '../../../core/models/item-by-space';
import {PiiItem} from '../../../core/models/pii-item';
import {RawStreamPayload} from '../../../core/models/stream-event-type';
import {SentinelleApiService} from '../../../core/services/sentinelle-api.service';
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
  private readonly sentinelleApiService = inject(SentinelleApiService);
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  // In-memory storage of PII items per space
  readonly itemsBySpace = signal<ItemsBySpace>({});

  // Computed: total items across all spaces
  readonly totalItemsCount = computed(() => {
    const map = this.itemsBySpace();
    return Object.values(map).reduce((sum, items) => sum + items.length, 0);
  });

  // Computed: spaces with detected PII
  readonly spacesWithPii = computed(() => {
    const map = this.itemsBySpace();
    return Object.keys(map).filter(key => (map[key]?.length ?? 0) > 0);
  });

  /**
   * Gets PII items for a specific space.
   */
  getItemsForSpace(spaceKey: string): PiiItem[] {
    return this.itemsBySpace()[spaceKey] ?? [];
  }

  /**
   * Gets severity counts for a specific space.
   */
  getCountsForSpace(spaceKey: string): { high: number; medium: number; low: number } {
    const items = this.getItemsForSpace(spaceKey);
    return this.spacesDashboardUtils.severityCounts(items);
  }

  /**
   * Adds a PII item to the in-memory store for a space.
   *
   * Business rules:
   * - Skips items without entities (no empty cards)
   * - Deduplicates by pageId + attachmentName
   * - Keeps max 400 items per space (FIFO)
   * - Updates counts in SpacesDashboardUtils after adding
   */
  addPiiItemToSpace(spaceKey: string, payload: RawStreamPayload): void {
    const entities = Array.isArray(payload.detectedEntities) ? payload.detectedEntities : [];

    // Skip creating a card when no PII entities were detected
    if (!entities.length) {
      return;
    }

    const severity = this.sentinelleApiService.severityForEntities(entities);

    const piiItem: PiiItem = {
      scanId: payload.scanId ?? '',
      spaceKey: spaceKey,
      pageId: String(payload.pageId ?? ''),
      pageTitle: payload.pageTitle,
      pageUrl: payload.pageUrl,
      emittedAt: payload.emittedAt,
      isFinal: !!payload.isFinal,
      severity,
      summary: (payload.summary && typeof payload.summary === 'object') ? payload.summary : undefined,
      detectedEntities: entities.map((e: any) => {
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
      return;
    }

    // Add new item at the beginning (most recent first)
    const nextItems = [piiItem, ...previous];

    // Keep max 400 items per space
    if (nextItems.length > 400) {
      nextItems.length = 400;
    }

    this.itemsBySpace.set({ ...this.itemsBySpace(), [spaceKey]: nextItems });

    // Update counts in UI decoration immediately
    this.updateCountsForSpace(spaceKey);
  }

  /**
   * Adds multiple PII items for a space (used for loading persisted items).
   * Applies same deduplication and limit rules as addPiiItemToSpace.
   */
  addItemsForSpace(spaceKey: string, items: PiiItem[]): void {
    const existing = this.itemsBySpace()[spaceKey] ?? [];
    const merged = [...items, ...existing];

    // Deduplicate by pageId + attachmentName
    const seen = new Set<string>();
    const deduped = merged.filter(item => {
      const key = `${item.pageId}||${item.attachmentName || ''}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });

    // Keep max 400 items
    if (deduped.length > 400) {
      deduped.length = 400;
    }

    this.itemsBySpace.set({ ...this.itemsBySpace(), [spaceKey]: deduped });

    // Update counts in UI decoration
    this.updateCountsForSpace(spaceKey);
  }

  /**
   * Updates severity counts for a space in SpacesDashboardUtils.
   */
  private updateCountsForSpace(spaceKey: string): void {
    const items = this.getItemsForSpace(spaceKey);
    const counts = this.spacesDashboardUtils.severityCounts(items);
    this.spacesDashboardUtils.updateSpace(spaceKey, { counts });
  }

  /**
   * Applies counts from items to all spaces with PII.
   * Business purpose: Bulk update of counts after loading persisted items.
   */
  applyCountsFromItems(): void {
    const map = this.itemsBySpace();
    for (const key of Object.keys(map)) {
      this.updateCountsForSpace(key);
    }
  }

  /**
   * Clears all items for a specific space.
   */
  clearItemsForSpace(spaceKey: string): void {
    const map = { ...this.itemsBySpace() };
    delete map[spaceKey];
    this.itemsBySpace.set(map);

    // Reset counts to zero
    this.spacesDashboardUtils.updateSpace(spaceKey, {
      counts: { total: 0, high: 0, medium: 0, low: 0 }
    });
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


  /**
   * Gets statistics about stored items.
   */
  getStatistics(): {
    totalSpaces: number;
    totalItems: number;
    spacesWithPii: number;
    averageItemsPerSpace: number;
  } {
    const map = this.itemsBySpace();
    const keys = Object.keys(map);
    const totalSpaces = keys.length;
    const totalItems = this.totalItemsCount();
    const spacesWithPii = this.spacesWithPii().length;
    const averageItemsPerSpace = totalSpaces > 0 ? totalItems / totalSpaces : 0;

    return {
      totalSpaces,
      totalItems,
      spacesWithPii,
      averageItemsPerSpace: Math.round(averageItemsPerSpace * 100) / 100
    };
  }
}
