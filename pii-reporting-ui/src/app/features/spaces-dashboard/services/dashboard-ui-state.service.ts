import {computed, inject, Injectable, signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {HistoryEntry} from '../../../core/models/history-entry';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';

/**
 * Service responsible for managing dashboard UI state.
 *
 * Business purpose:
 * - Manages row expansion/collapse state for spaces table
 * - Tracks selected space for highlighting and focus
 * - Maintains scan history per space (running/completed/failed)
 * - Provides logging functionality for user feedback
 * - Offers UI helpers for status labels and severity mapping
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles UI state management
 * - Open/Closed: Can be extended with new UI state without modification
 * - Dependency Inversion: Depends on TranslocoService abstraction
 *
 * Business Rules:
 * - Max 1000 log lines kept in memory (auto-pruned)
 * - Row expansion automatically selects the space
 * - History tracks latest status per space (upsert pattern)
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardUiStateService {
  private readonly translocoService = inject(TranslocoService);
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  // Row expansion state for PrimeNG table
  readonly expandedRowKeys = signal<Record<string, boolean>>({});

  // Currently selected space key for highlighting
  readonly selectedSpaceKey = signal<string | null>(null);

  // Currently selected spaces for bulk action
  readonly selectedSpaces = signal<any[]>([]);

  // Currently active space being scanned (for SSE tracking)
  readonly activeSpaceKey = signal<string | null>(null);

  // Mask PII by default in UI
  readonly maskByDefault = signal(true);

  // Log lines for user feedback (max 1000 lines)
  readonly lines = signal<string[]>([]);

  // Scan history per space (tracks status transitions)
  readonly history = signal<HistoryEntry[]>([]);

  // Computed: number of expanded rows
  readonly expandedRowCount = computed(() => {
    return Object.keys(this.expandedRowKeys()).length;
  });

  // Computed: whether any space is selected
  readonly hasSelectedSpace = computed(() => {
    return this.selectedSpaceKey() !== null;
  });

  // Computed: number of selected spaces
  readonly selectedSpacesCount = computed(() => {
    return this.selectedSpaces().length;
  });

  /**
   * Handles row expansion event from PrimeNG table.
   * Business rule: Expanding a row automatically selects it.
   */
  onRowExpand(event: { data?: { key?: string } }): void {
    const key = event?.data?.key;
    if (!key) return;

    const map = this.expandedRowKeys();
    if (!map[key]) {
      this.expandedRowKeys.set({ ...map, [key]: true });
    }

    this.selectedSpaceKey.set(key);
  }

  /**
   * Handles row collapse event from PrimeNG table.
   */
  onRowCollapse(event: { data?: { key?: string } }): void {
    const key = event?.data?.key;
    if (!key) return;

    const map = { ...this.expandedRowKeys() };
    if (map[key]) {
      delete map[key];
      this.expandedRowKeys.set(map);
    }
  }

  /**
   * Expands a specific space row programmatically.
   */
  expandRow(spaceKey: string): void {
    const map = this.expandedRowKeys();
    if (!map[spaceKey]) {
      this.expandedRowKeys.set({ ...map, [spaceKey]: true });
    }
  }

  /**
   * Collapses a specific space row programmatically.
   */
  collapseRow(spaceKey: string): void {
    const map = { ...this.expandedRowKeys() };
    if (map[spaceKey]) {
      delete map[spaceKey];
      this.expandedRowKeys.set(map);
    }
  }

  /**
   * Collapses all expanded rows.
   */
  collapseAllRows(): void {
    this.expandedRowKeys.set({});
  }

  /**
   * Sets the selected space key.
   */
  selectSpace(spaceKey: string | null): void {
    this.selectedSpaceKey.set(spaceKey);
  }

  /**
   * Appends a log line to the activity log.
   * Business rule: Max 1000 lines kept in memory (FIFO).
   */
  append(line: string): void {
    const next = [...this.lines(), line];
    if (next.length > 1000) {
      next.splice(0, next.length - 1000);
    }
    this.lines.set(next);
  }

  /**
   * Clears all log lines.
   */
  clearLogs(): void {
    this.lines.set([]);
  }

  /**
   * Upserts scan history entry for a space.
   * Business rule: Only one history entry per space (latest status wins).
   */
  upsertScanHistory(spaceKey: string, status: 'running' | 'completed' | 'failed'): void {
    const index = this.history().findIndex((h) => h.spaceKey === spaceKey);

    if (index >= 0) {
      const copy = [...this.history()];
      copy[index] = { ...copy[index], status };
      this.history.set(copy);
      return;
    }

    this.history.set([...this.history(), { spaceKey, status }]);
  }

  /**
   * Gets scan history for a specific space.
   */
  getScanHistory(spaceKey: string): HistoryEntry | undefined {
    return this.history().find(h => h.spaceKey === spaceKey);
  }

  /**
   * Clears scan history for all spaces.
   */
  clearHistory(): void {
    this.history.set([]);
  }

  /**
   * Gets the translated label for a status.
   * Business purpose: Provides human-readable status text for UI display.
   */
  statusLabel(status?: string): string {
    const key = this.getStatusKey(status);
    return this.translocoService.translate(key);
  }

  /**
   * Maps status to translation key.
   */
  private getStatusKey(status?: string): string {
    switch (status?.toUpperCase()) {
      case 'RUNNING':
        return 'dashboard.status.running';
      case 'OK':
        return 'dashboard.status.ok';
      case 'FAILED':
        return 'dashboard.status.failed';
      case 'PENDING':
        return 'dashboard.status.pending';
      case 'PAUSED':
        return 'dashboard.status.paused';
      case 'COMPLETED' :
        return 'dashboard.status.completed';
      default:
        return 'dashboard.status.pending';
    }
  }

  /**
   * Maps status to PrimeNG severity for styling.
   * Business purpose: Provides visual feedback for different scan states.
   */
  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    return this.spacesDashboardUtils.statusStyle(status);
  }

  /**
   * Resets all UI state to initial values.
   * Business purpose: Clean slate for new scan or dashboard refresh.
   */
  reset(): void {
    this.expandedRowKeys.set({});
    this.selectedSpaceKey.set(null);
    this.activeSpaceKey.set(null);
    this.selectedSpaces.set([]);
    this.lines.set([]);
    this.history.set([]);
  }
}