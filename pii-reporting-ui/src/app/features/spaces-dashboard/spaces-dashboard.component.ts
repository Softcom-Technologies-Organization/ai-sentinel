import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {TranslocoModule, TranslocoService} from '@jsverse/transloco';
import {
  LanguageSelectorComponent
} from '../../core/components/language-selector/language-selector.component';
import {PiiItemCardComponent} from '../pii-item-card/pii-item-card.component';
import {
  LastScanMeta,
  SentinelleApiService,
  SpaceScanStateDto
} from '../../core/services/sentinelle-api.service';
import {Subscription} from 'rxjs';
import {
  ConfluenceSpacesPollingService
} from '../../core/services/confluence-spaces-polling.service';
import {Space} from '../../core/models/space';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {BadgeModule} from 'primeng/badge';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {TagModule} from 'primeng/tag';
import {SpacesDashboardUtils} from './spaces-dashboard.utils';
import {
  coerceSpaceKey,
  formatEventLog,
  isAttachmentPayload,
  StreamEventType
} from './spaces-dashboard-stream.utils';
import {RawStreamPayload} from '../../core/models/stream-event-type';
import {HistoryEntry} from '../../core/models/history-entry';
import {ItemsBySpace} from '../../core/models/item-by-space';
import {PiiItem} from '../../core/models/pii-item';
import {SpaceUpdateInfo} from '../../core/models/space-update-info.model';
import {Ripple} from 'primeng/ripple';
import {TooltipModule} from 'primeng/tooltip';
import {DataViewModule} from 'primeng/dataview';
import {ProgressBarModule} from 'primeng/progressbar';
import {SkeletonModule} from 'primeng/skeleton';
import {ConfirmationService, MessageService, SortEvent} from 'primeng/api';
import {ToastService} from '../../core/services/toast.service';
import {TestIds} from '../test-ids.constants';
import {ToastModule} from 'primeng/toast';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {ScanProgressService} from '../../core/services/scan-progress.service';
import {NewSpacesBannerComponent} from '../../shared/components/new-spaces-banner/new-spaces-banner.component';
import {SpaceFiltersComponent} from '../../shared/components/space-filters/space-filters.component';


/**
 * Dashboard to orchestrate scanning all Confluence spaces sequentially.
 * Business goal: allow starting a multi-space scan, follow progress, and inspect results per space.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, ToggleSwitchModule, PiiItemCardComponent, BadgeModule, InputTextModule, SelectModule, TableModule, TagModule, Ripple, TooltipModule, DataViewModule, ProgressBarModule, SkeletonModule, ConfirmDialogModule, ToastModule, TranslocoModule, LanguageSelectorComponent, NewSpacesBannerComponent, SpaceFiltersComponent],
  providers: [ConfirmationService, MessageService, ToastService],
  templateUrl: './spaces-dashboard.component.html',
  styleUrl: './spaces-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpacesDashboardComponent implements OnInit, OnDestroy {
  readonly sentinelleApiService = inject(SentinelleApiService);
  readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  readonly pollingService = inject(ConfluenceSpacesPollingService);
  readonly toastService = inject(ToastService);
  readonly confirmationService = inject(ConfirmationService);
  readonly translocoService = inject(TranslocoService);
  readonly scanProgressService = inject(ScanProgressService);

  private sub?: Subscription;
  private pollingSub?: Subscription;

  // Expose test IDs to template for E2E testing
  readonly testIds = TestIds.dashboard;

  readonly globalFilter = signal<string>('');
  readonly statusFilter = signal<string | null>(null);
  readonly isStreaming = signal(false);
  readonly spaces = signal<Space[]>([]);
  readonly queue = signal<string[]>([]);
  readonly activeSpaceKey = signal<string | null>(null);
  readonly selectedSpaceKey = signal<string | null>(null);
  readonly history = signal<HistoryEntry[]>([]);
  readonly itemsBySpace = signal<ItemsBySpace>({});
  readonly maskByDefault = signal(true);
  readonly lines = signal<string[]>([]);
  // Track expanded rows by key for PrimeNG row expansion
  readonly expandedRowKeys = signal<Record<string, boolean>>({});
  // 10 placeholder rows for loading skeleton
  readonly skeletonRows: number[] = Array.from({ length: 10 }, (_, i) => i);

  // Latest scan info for resume and dashboard display
  readonly lastScanMeta = signal<LastScanMeta | null>(null);
  readonly lastSpaceStatuses = signal<SpaceScanStateDto[]>([]);
  readonly isResuming = signal<boolean>(false);

  // Loading state for spaces list to control UI availability
  readonly isSpacesLoading = signal<boolean>(true);
  readonly canStartScan = computed(() => !this.isStreaming() && !this.isSpacesLoading() && this.spaces().length > 0);
  readonly canResumeScan = computed(() => !this.isStreaming() && !this.isSpacesLoading() && !!this.lastScanMeta() && !this.isResuming());

  // Sorting state
  readonly sortField = signal<string | null>(null);
  readonly sortOrder = signal<number>(1); // 1 for ascending, -1 for descending

  // Manual refresh state (Phase 1)
  readonly lastRefresh = signal<Date | null>(null);
  readonly isRefreshing = signal<boolean>(false);

  // Notification state (Phase 2)
  readonly hasNewSpaces = signal<boolean>(false);
  readonly newSpacesCount = signal<number>(0);

  // Space update info (to detect spaces modified since last scan)
  readonly spacesUpdateInfo = signal<SpaceUpdateInfo[]>([]);

  ngOnInit(): void {
    this.fetchSpaces();
    this.loadLastScan();
    this.loadSpacesUpdateInfo();
  }

  ngOnDestroy(): void {
    this.stopCurrentScan();
    this.pollingSub?.unsubscribe();
  }

  readonly filteredSpaces = computed(() => {
    // Delegate to UI facade to ensure spaces are decorated with defaults (status/counts)
    return this.spacesDashboardUtils.filteredSpaces();
  });

  /**
   * Sorted spaces based on current sort field and order.
   * Supports sorting by:
   * - name: alphabetical order
   * - piiCount: by priority - high first, then medium, then low
   */
  readonly sortedSpaces = computed(() => {
    const spaces = [...this.filteredSpaces()];
    const field = this.sortField();
    const order = this.sortOrder();

    if (!field) {
      return spaces;
    }

    return spaces.sort((a, b) => {
      let compareValue = 0;

      if (field === 'name') {
        const nameA = (a.name ?? '').toLowerCase();
        const nameB = (b.name ?? '').toLowerCase();
        compareValue = nameA.localeCompare(nameB);
      } else if (field === 'piiCount') {
        // Sort by priority: high > medium > low (descending for each)
        const priorities = ['high', 'medium', 'low'] as const;

        for (const priority of priorities) {
          const countA = a.counts?.[priority] ?? 0;
          const countB = b.counts?.[priority] ?? 0;

          if (countA !== countB) {
            compareValue = countB - countA;
            break;
          }
        }
      }

      return compareValue * order;
    });
  });

  onGlobalChange(v: string): void {
    this.globalFilter.set(v);
    this.spacesDashboardUtils.globalFilter.set(v);
  }

  /**
   * Handle custom sort event from PrimeNG table.
   * Updates sort field and order signals to trigger sortedSpaces() recomputation.
   */
  onCustomSort(event: SortEvent): void {
    if (!event.field) {
      this.sortField.set(null);
      this.sortOrder.set(1);
      return;
    }

    this.sortField.set(event.field);
    this.sortOrder.set(event.order ?? 1);
  }

  // UI actions
  startAll(): void {
    if (this.isStreaming()) {
      return;
    }

    // Display confirmation before starting the scan
    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.globalScan.header'),
      message: this.translocoService.translate('confirmations.globalScan.message'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translocoService.translate('confirmations.globalScan.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.globalScan.rejectLabel'),
      acceptButtonStyleClass: 'p-button-primary',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => {
        this.executeStartAll();
      },
      reject: () => {
        this.append(this.translocoService.translate('dashboard.logs.scanCancelled'));
      }
    });
  }

  private executeStartAll(): void {
    this.stopCurrentScan();
    // Clear previous dashboard results before starting a brand-new scan
    this.resetDashboardForNewScan();

    this.append(this.translocoService.translate('dashboard.logs.purging'));
    this.sentinelleApiService.purgeAllScans().subscribe({
      next: () => {
        this.append(this.translocoService.translate('dashboard.logs.purgeOk'));
        this.isStreaming.set(true);
        this.sub = this.sentinelleApiService.startAllSpacesStream().subscribe({
          next: (ev) => this.routeStreamEvent(ev.type as StreamEventType, ev.data),
          error: (err) => {
            this.append(this.translocoService.translate('dashboard.logs.sseError', { error: err?.message ?? err }));
            this.isStreaming.set(false);
          }
        });
      },
      error: (err) => {
        this.append(this.translocoService.translate('dashboard.logs.purgeError', { error: err?.message ?? err }));
        this.isStreaming.set(false);
      }
    });
  }

  /**
   * Reset dashboard state and UI decoration when starting a completely new scan.
   * Business rule: do not apply this on resume, as resume should continue displaying existing results.
   */
  private resetDashboardForNewScan(): void {
    // Clear in-memory per-space items and progress
    this.itemsBySpace.set({});
    this.scanProgressService.resetAllProgress();
    this.history.set([]);
    // Collapse all rows and reset selection/active markers
    this.expandedRowKeys.set({});
    this.selectedSpaceKey.set(null);
    this.activeSpaceKey.set(null);
    // Clear previous error toasts when starting a new scan
    this.toastService.clearScanErrors();
    // Reinitialize UI decoration (status/counts/lastScanTs) for all known spaces
    try {
      this.spacesDashboardUtils.setSpaces(this.spaces());
    } catch {
      // no-op: defensive in case spaces() not ready yet
    }
  }

  stopCurrentScan(): void {
    const scanId = this.lastScanMeta()?.scanId;

    // Unsubscribe from SSE stream
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }
    this.isStreaming.set(false);

    // Call backend to mark scan as PAUSED if we have a scanId
    if (scanId) {
      this.sentinelleApiService.pauseScan(scanId).subscribe({
        next: () => {
          this.append(this.translocoService.translate('dashboard.logs.scanPaused', { scanId }));
          this.loadLastSpaceStatuses(false);
          this.loadLastScan();
        },
        error: (err) => {
          this.append(this.translocoService.translate('dashboard.logs.pauseError', { error: err?.message ?? err }));
          // Still reload statuses even if pause fails
          this.loadLastSpaceStatuses(false);
          this.loadLastScan();
        }
      });
    } else {
      // No scanId, just reload statuses
      this.loadLastSpaceStatuses(false);
      this.loadLastScan();
    }
  }

  // --- Latest scan loading and resume ---
  private loadLastScan(): void {
    this.sentinelleApiService.getLastScanMeta().subscribe({
      next: (meta) => {
        this.lastScanMeta.set(meta);
        if (meta) {
          this.append(this.translocoService.translate('dashboard.logs.lastScanDetected', { scanId: meta.scanId, count: meta.spacesCount }));
          this.loadLastSpaceStatuses();
        } else {
          this.append(this.translocoService.translate('dashboard.logs.noLastScan'));
        }
      },
      error: () => {
        this.lastScanMeta.set(null);
      }
    });
  }

  /**
   * Load dashboard summary using unified endpoint that combines authoritative progress
   * from checkpoints with aggregated counters from events. This replaces the previous
   * dual API calls (getLastScanSpaceStatuses + getLastScanItems) that caused race
   * conditions where progress was overwritten with intermediate values.
   *
   * Business rule:
   * - If NO active scan: apply summary data to ALL spaces
   * - If scan IS active: apply summary data ONLY to COMPLETED spaces (preserve SSE real-time data for others)
   */
  private loadLastSpaceStatuses(alsoLoadItems: boolean = true): void {
    this.sentinelleApiService.getDashboardSpacesSummary().subscribe({
      next: (summary) => {
        if (!summary) {
          this.lastSpaceStatuses.set([]);
          return;
        }

        // Convert SpaceSummaryDto to SpaceScanStateDto format for compatibility
        const spaceScanStateList = summary.spaces.map(space => ({
          spaceKey: space.spaceKey,
          status: space.status,
          pagesDone: space.pagesDone,
          attachmentsDone: space.attachmentsDone,
          lastEventTs: space.lastEventTs,
          progressPercentage: space.progressPercentage ?? undefined
        }));

        this.lastSpaceStatuses.set(spaceScanStateList);
        const isActive = this.isStreaming();

        for (const spaceSummary of summary.spaces) {
          // BUSINESS RULE: If scan is active, only apply summary data to COMPLETED spaces
          // This preserves real-time SSE data for spaces that are RUNNING, PENDING, or FAILED
          if (isActive && spaceSummary.status !== 'COMPLETED') {
            continue;
          }

          const spaceScanState = {
            spaceKey: spaceSummary.spaceKey,
            status: spaceSummary.status,
            pagesDone: spaceSummary.pagesDone,
            attachmentsDone: spaceSummary.attachmentsDone,
            lastEventTs: spaceSummary.lastEventTs,
            progressPercentage: spaceSummary.progressPercentage ?? undefined
          };

          const uiStatus = this.computeUiStatus(spaceScanState, isActive);
          this.spacesDashboardUtils.updateSpace(spaceSummary.spaceKey, {
            status: uiStatus,
            lastScanTs: spaceSummary.lastEventTs
          });

          // CRITICAL: Always use progress from checkpoint (authoritative source)
          // This fixes the bug where intermediate progress was overwriting correct values
          if (spaceSummary.status === 'COMPLETED') {
            // Fallback to 100% for completed scans without explicit progress
            this.scanProgressService.updateProgress(spaceSummary.spaceKey, { percent: 100 });
          } else if (spaceSummary.progressPercentage != null) {
            this.scanProgressService.updateProgress(spaceSummary.spaceKey, {
              percent: spaceSummary.progressPercentage
            });
          }
        }

        // Apply counts from any items already loaded
        this.applyCountsFromItems();

        // Still load persisted items to display PII cards (if needed)
        // The unified endpoint provides progress and counters, but not the detailed PII items
        if (alsoLoadItems) {
          this.loadLastItems();
        }
      },
      error: () => {
        this.lastSpaceStatuses.set([]);
      }
    });
  }

  private computeUiStatus(
    spaceScanState: SpaceScanStateDto,
    isActive: boolean
  ): 'FAILED' | 'RUNNING' | 'OK' | 'PENDING' | 'PAUSED' | undefined {
    if (spaceScanState.status === 'COMPLETED') return 'OK';
    if (spaceScanState.status === 'FAILED') return 'FAILED';
    if (spaceScanState.status === 'PENDING') return 'PAUSED';
    if (spaceScanState.status === 'RUNNING' && isActive) return 'RUNNING';
    const workDone = (spaceScanState.pagesDone ?? 0) + (spaceScanState.attachmentsDone ?? 0);
    if (workDone > 0) return 'PAUSED';
    return 'PENDING';
  }

  private loadLastItems(): void {
    this.sentinelleApiService.getLastScanItems().subscribe({
      next: (events) => {
        for (const event of events) {
          const type = (event as any)?.eventType as string | undefined;
          if (type !== 'item' && type !== 'attachmentItem') continue;
          const incomingKey = coerceSpaceKey(event);
          if (!incomingKey) continue;
          this.addPiiItemToSpace(incomingKey, event);
        }
        // After adding items, compute counts for each space
        this.applyCountsFromItems();
      },
      error: () => {
        // ignore
      }
    });
  }

  private applyCountsFromItems(): void {
    const map = this.itemsBySpace();
    for (const key of Object.keys(map)) {
      const items = map[key] ?? [];
      const counts = this.spacesDashboardUtils.severityCounts(items);
      this.spacesDashboardUtils.updateSpace(key, { counts });
    }
  }

  resumeLastScan(): void {
    const meta = this.lastScanMeta();
    if (!meta || this.isStreaming() || this.isResuming()) return;
    this.isResuming.set(true);
    this.append(this.translocoService.translate('dashboard.logs.resumeRequest', { scanId: meta.scanId }));
    this.sentinelleApiService.resumeScan(meta.scanId).subscribe({
      next: () => {
        this.isResuming.set(false);
        // Immediately reconnect to the SSE stream so new WebFlux events are displayed live
        this.append(this.translocoService.translate('dashboard.logs.resumeAccepted'));
        // Ensure no leftover subscription then open the stream
        this.isStreaming.set(true);
        this.sub = this.sentinelleApiService.startAllSpacesStream(meta.scanId).subscribe({
          next: (ev) => this.routeStreamEvent(ev.type as StreamEventType, ev.data),
          error: (err) => {
            this.append(this.translocoService.translate('dashboard.logs.sseError', { error: err?.message ?? err }));
            this.isStreaming.set(false);
          }
        });
        // Also refresh statuses and backfill persisted items to cover the gap while SSE was disconnected.
        // Duplicates are avoided by addPiiItemToSpace() via page_id + attachmentName dedup.
        this.loadLastSpaceStatuses(true);
      },
      error: (e) => {
        this.isResuming.set(false);
        this.append(this.translocoService.translate('dashboard.logs.resumeError', { error: e?.message ?? e }));
      }
    });
  }

  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    if (field === 'status') {
      this.statusFilter.set(value ?? null);
    }
    this.spacesDashboardUtils.onFilter(field, value);
  }

  get statusOptions() {
    return this.spacesDashboardUtils.statusOptions();
  }

  statusLabel(status?: string): string {
    const key = this.getStatusKey(status);
    return this.translocoService.translate(key);
  }

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
      default:
        return 'dashboard.status.pending';
    }
  }

  statusSeverity(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    return this.spacesDashboardUtils.statusSeverity(status);
  }

  openConfluence(space: any): void {
    this.spacesDashboardUtils.openConfluence(space);
  }

  onRowExpand(event: { data?: { key?: string } }): void {
    const key = event?.data?.key;
    if (!key) return;
    const map = this.expandedRowKeys();
    if (!map[key]) {
      this.expandedRowKeys.set({ ...map, [key]: true });
    }
    this.selectedSpaceKey.set(key);
  }

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
   * Fetches Confluence spaces from backend cache.
   * Business purpose: loads space list for dashboard display with instant response from DB cache.
   */
  private fetchSpaces(): void {
    this.isSpacesLoading.set(true);
    this.isRefreshing.set(true);
    this.sentinelleApiService.getSpaces().subscribe({
      next: (spaces) => {
        this.spaces.set(spaces);
        this.queue.set(spaces.map((s) => s.key));
        this.spacesDashboardUtils.setSpaces(spaces);
        // Re-apply cached last scan statuses and PII counts once spaces are available
        this.reapplyLastScanUi();
        this.lastRefresh.set(new Date());
        this.isSpacesLoading.set(false);
        this.isRefreshing.set(false);

        // Start polling after spaces are loaded to avoid false "new spaces" detection
        if (!this.pollingSub) {
          this.startBackgroundPolling();
        }
      },
      error: (e) => {
        this.append(this.translocoService.translate('dashboard.logs.fetchSpacesError', { error: e?.message ?? e }));
        this.isSpacesLoading.set(false);
        this.isRefreshing.set(false);
      }
    });
  }

  /**
   * Manually refreshes the spaces list.
   * Business purpose: allows users to explicitly update the dashboard with latest cached data.
   */
  refreshSpaces(): void {
    this.fetchSpaces();
    this.hasNewSpaces.set(false);
    this.newSpacesCount.set(0);
  }

  /**
   * Starts silent background polling to detect new spaces.
   * Business purpose: automatic discovery of new spaces without disrupting user workflow.
   */
  private startBackgroundPolling(): void {
    const initialCount = this.spaces().length;

    this.pollingSub = this.pollingService.startPolling(initialCount).subscribe({
      next: (detection) => {
        if (detection.hasNewSpaces) {
          this.hasNewSpaces.set(true);
          this.newSpacesCount.set(detection.newSpacesCount);
        }
      },
      error: (err) => {
        console.error('[polling] Error during background polling:', err);
      }
    });
  }

  /**
   * Dismisses the new spaces notification banner.
   * Business purpose: allows users to clear notification without refreshing.
   */
  dismissNotification(): void {
    this.hasNewSpaces.set(false);
    this.newSpacesCount.set(0);
  }

  /**
   * Loads space update information to detect which spaces have been modified since last scan.
   * Business purpose: enables visual indicators for spaces that may need re-scanning.
   */
  private loadSpacesUpdateInfo(): void {
    this.sentinelleApiService.getSpacesUpdateInfo().subscribe({
      next: (updateInfos) => {
        this.spacesUpdateInfo.set(updateInfos);
      },
      error: (err) => {
        console.error('[ui] Error loading spaces update info:', err);
        this.spacesUpdateInfo.set([]);
      }
    });
  }

  /**
   * Checks if a specific space has been updated since its last scan.
   * Business purpose: used by template to show update indicator icons.
   */
  hasSpaceBeenUpdated(spaceKey: string): boolean {
    const info = this.spacesUpdateInfo().find(i => i.spaceKey === spaceKey);
    return info?.hasBeenUpdated ?? false;
  }

  /**
   * Gets the update tooltip text for a space.
   * Business purpose: provides human-readable details about what changed (pages/attachments).
   */
  getSpaceUpdateTooltip(spaceKey: string): string {
    const info = this.spacesUpdateInfo().find(i => i.spaceKey === spaceKey);
    if (!info?.hasBeenUpdated) {
      return '';
    }

    const maxPerCategory = 5;

    const parts: string[] = [];

    const pages = Array.isArray(info.updatedPages) ? info.updatedPages : [];
    if (pages.length > 0) {
      const shown = pages.slice(0, maxPerCategory);
      const more = pages.length - shown.length;
      const andMore = more > 0 ? `\n${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.andMore', { count: more })}` : '';
      const list = `- ${shown.join('\n- ')}${andMore}`;
      parts.push(`${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.pagesModified')}\n${list}`);
    }

    const attachments = Array.isArray(info.updatedAttachments) ? info.updatedAttachments : [];
    if (attachments.length > 0) {
      const shown = attachments.slice(0, maxPerCategory);
      const more = attachments.length - shown.length;
      const andMore = more > 0 ? `\n${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.andMore', { count: more })}` : '';
      const list = `- ${shown.join('\n- ')}${andMore}`;
      parts.push(`${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.attachmentsModified')}\n${list}`);
    }

    // Fallback if no specific lists were provided by the backend
    if (parts.length === 0) {
      return this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.contentModified');
    }

    // Note: do NOT include last scan date in tooltip per requirement
    const currentLang = this.translocoService.getActiveLang();
    const locale = currentLang === 'en' ? 'en-US' : 'fr-FR';
    const dateStr = info.lastModified ? new Date(info.lastModified).toLocaleString(locale) : this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.updated', { date: 'Unknown' });
    const updatedLabel = info.lastModified ? this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.updated', { date: dateStr }) : dateStr;
    return updatedLabel + '\n'+ parts.join('\n\n');
  }

  /**
   * Re-applies last known statuses and PII counts to UI spaces when the base list is (re)loaded.
   * Fixes race condition where loadLastScan() may finish before fetchSpaces(), causing badges not to refresh.
   */
  private reapplyLastScanUi(): void {
    const list = this.lastSpaceStatuses();
    if (!Array.isArray(list) || list.length === 0) {
      this.applyCountsFromItems();
      return;
    }
    const isActive = this.isStreaming();
    for (const s of list) {
      const uiStatus = this.computeUiStatus(s, isActive);
      this.spacesDashboardUtils.updateSpace(s.spaceKey, { status: uiStatus, lastScanTs: s.lastEventTs });
      if (s.status === 'COMPLETED') {
        this.scanProgressService.updateProgress(s.spaceKey, { percent: s.progressPercentage ?? 100 });
      }
    }
    // Recompute counts from already loaded items (if any)
    this.applyCountsFromItems();
  }

  /**
   * Ensure we capture the current scan metadata (scanId/emittedAt) from an incoming SSE payload.
   * Used to enable the Resume button after a first interruption even if the backend meta has not been fetched yet.
   */
  private ensureLastScanMetaFromPayload(payload: RawStreamPayload): void {
    try {
      const id = (payload as any)?.scanId as string | undefined;
      if (!id) return;
      const current = this.lastScanMeta();
      if (!current || current.scanId !== id) {
        const ts = (payload as any)?.ts ?? new Date().toISOString();
        const spacesCount = Array.isArray(this.spaces()) ? this.spaces().length : 0;
        this.lastScanMeta.set({ scanId: id, lastUpdated: ts, spacesCount });
      }
    } catch {
      // ignore
    }
  }

  private routeStreamEvent(type: StreamEventType, payload?: RawStreamPayload): void {
    this.append(formatEventLog(type, JSON.stringify(payload ?? {})));

    // Handle multiStart early to refresh dashboard even if payload is missing
    if (type === 'multiStart') {
      this.handleAllSpaceScanStart(payload);
      return;
    }

    if (!payload) {
      return;
    }

    switch (type) {
      case 'start': {
        this.handleStreamScanStart(payload);
        break;
      }
      case 'pageStart': {
        this.handlePageStart(payload);
        break;
      }
      case 'item':
      case 'attachmentItem': {
        this.handleItemEvent(payload);
        break;
      }
      case 'scanError': {
        this.handleStreamError(payload);
        break;
      }
      case 'complete': {
        this.handleStreamComplete(payload);
        break;
      }
      case 'multiComplete': {
        this.isStreaming.set(false);
        this.stopCurrentScan();
        break;
      }
      default: {
        // No-op for other types
        break;
      }
    }
  }

  /**
   * Handle the global multi-space start event by refreshing the dashboard immediately.
   * - Ensures streaming state is on
   * - Captures scanId from payload when available
   * - Marks all spaces as PENDING and rebuilds the queue order
   */
  private handleAllSpaceScanStart(payload?: RawStreamPayload): void {
    this.isStreaming.set(true);
    if (payload) {
      this.ensureLastScanMetaFromPayload(payload);
    }
    const spaces = this.spaces();
    if (Array.isArray(spaces) && spaces.length > 0) {
      // Rebuild queue with all known space keys in their current display order
      this.queue.set(spaces.map((s) => s.key));
      // Reset status to PENDING to reflect a fresh multi-space scan
      for (const s of spaces) {
        this.spacesDashboardUtils.updateSpace(s.key, { status: 'PENDING' });
      }
      if (!this.selectedSpaceKey()) {
        this.selectedSpaceKey.set(spaces[0].key);
      }
    }
  }

  /**
   * Marks the space as running, initializes progress, and updates UI model.
   */
  private handleStreamScanStart(payload: RawStreamPayload): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }
    // Ensure UI knows the current scanId early so the Resume button can be enabled after interruption
    this.ensureLastScanMetaFromPayload(payload);

    this.activeSpaceKey.set(spaceKey);
    if (!this.selectedSpaceKey()) {
      this.selectedSpaceKey.set(spaceKey);
    }
    this.queue.set(this.queue().filter((queuedKey) => queuedKey !== spaceKey));

    const current = this.scanProgressService.getProgress()[spaceKey]?.percent;
    const percent = this.extractPercent(payload) ?? current ?? 0;
    const total = (payload as any).pagesTotal as number | undefined;
    const prevTotal = this.scanProgressService.getProgress()[spaceKey]?.total;
    this.updateProgress(spaceKey, { total: total ?? prevTotal, index: 0, percent });

    this.upsertScanHistory(spaceKey, 'running');
    // Update UI model to reflect running status immediately
    this.spacesDashboardUtils.updateSpace(spaceKey, { status: 'RUNNING' });
  }

  /**
   * Updates page progress for the given space.
   */
  private handlePageStart(payload: RawStreamPayload): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }
    const currentProgress = this.scanProgressService.getProgress()[spaceKey] ?? {};
    const total = (payload as any).pagesTotal ?? currentProgress.total;
    const index = (payload as any).pageIndex ?? currentProgress.index;
    let percent = this.extractPercent(payload);
    if (percent == null && typeof total === 'number' && typeof index === 'number' && total > 0) {
      percent = Math.round((index / total) * 100);
    }
    this.updateProgress(spaceKey, { total, index, percent });
  }

  /**
   * Handles both page and attachment item events by adding PII item and updating UI counts.
   */
  private handleItemEvent(payload: RawStreamPayload): void {
    const incomingKey = coerceSpaceKey(payload);
    const looksLikeAttachment = isAttachmentPayload(payload);
    const spaceKey = incomingKey ?? (looksLikeAttachment ? this.activeSpaceKey() : null);
    if (!spaceKey) {
      return;
    }
    if (!incomingKey && looksLikeAttachment) {
      this.append('[DEBUG_LOG] attachmentItem missing spaceKey; using activeSpaceKey=' + spaceKey);
    }

    this.addPiiItemToSpace(spaceKey, payload);

    // Reflect progress if backend provides percentage on item events
    // Skip progress update for completed scans to preserve persisted value
    const p = this.extractPercent(payload);
    if (p != null && payload.status !== 'COMPLETED') {
      this.updateProgress(spaceKey, { percent: p });
    }

    // Reflect counts and last timestamp in UI model for live PII column update
    const items = this.itemsBySpace()[spaceKey] ?? [];
    const counts = this.spacesDashboardUtils.severityCounts(items);
    const timestamp = (payload as any).ts ?? new Date().toISOString();
    this.spacesDashboardUtils.updateSpace(spaceKey, { counts, lastScanTs: timestamp, status: 'RUNNING' });
  }

  /**
   * Marks a space as failed on error events, displays a sticky toast notification, and updates UI.
   * IMPORTANT: Error events do not stop the scan - they are non-fatal and the scan continues.
   */
  private handleStreamError(payload: RawStreamPayload): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }

    // Extract error message from 'message' field (not 'errorMessage')
    const errorMessage = (payload as any)?.message ?? (payload as any)?.errorMessage ?? 'Erreur inconnue';
    const errorType = this.toastService.detectErrorType(errorMessage);

    // Display sticky error toast with full context
    this.toastService.showScanError({
      scanId: payload.scanId ?? '',
      spaceKey,
      pageId: payload.pageId == null ? undefined : String(payload.pageId),
      pageTitle: payload.pageTitle,
      attachmentName: (payload as any)?.attachmentName,
      errorMessage,
      errorType
    });

    // DO NOT mark the space as FAILED - errors are non-fatal
    // The scan continues and will emit complete event when done
    // Only update the last scan timestamp
    this.spacesDashboardUtils.updateSpace(spaceKey, { lastScanTs: new Date().toISOString() });

    // Log the error but do not update scan history to 'failed'
    // The space should remain in RUNNING status
    this.append(this.translocoService.translate('dashboard.logs.errorForSpace', { spaceKey, error: errorMessage }));
  }

  /**
   * Completes a space scan: finalize counts, mark status and clear active key if needed.
   */
  private handleStreamComplete(payload: RawStreamPayload): void {
    const spaceKey = payload.spaceKey;
    if (!spaceKey) {
      return;
    }
    this.upsertScanHistory(spaceKey, 'completed');
    this.updateProgress(spaceKey, { percent: 100 });

    // Finalize counts and mark as OK for the table
    const items = this.itemsBySpace()[spaceKey] ?? [];
    const counts = this.spacesDashboardUtils.severityCounts(items);
    this.spacesDashboardUtils.updateSpace(spaceKey, { status: 'OK', lastScanTs: new Date().toISOString(), counts });
    if (this.activeSpaceKey() === spaceKey) {
      this.activeSpaceKey.set(null);
    }
  }

  /**
   * Adds a PII item to the in-memory store for a space, keeping a max of 400 items.
   * Skips empty items (no entities).
   */
  private addPiiItemToSpace(spaceKey: string, payload: RawStreamPayload): void {
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
      // maskedHtml: this.sentinelleApiService.sanitizeMaskedHtml(payload.maskedContent),
      attachmentName: payload.attachmentName,
      attachmentType: payload.attachmentType,
      attachmentUrl: payload.attachmentUrl
    };
    const previous = this.itemsBySpace()[spaceKey] ?? [];
    // Deduplicate: skip if an item for the same page/attachment already exists
    const isDuplicate = previous.some(it => it.pageId === piiItem.pageId && it.attachmentName === piiItem.attachmentName);
    if (isDuplicate) {
      return;
    }
    const nextItems = [piiItem, ...previous];
    if (nextItems.length > 400) {
      nextItems.length = 400;
    }
    this.itemsBySpace.set({ ...this.itemsBySpace(), [spaceKey]: nextItems });
    // Update per-space PII recap counts immediately so the badges refresh as soon as details are fetched
    const counts = this.spacesDashboardUtils.severityCounts(nextItems);
    this.spacesDashboardUtils.updateSpace(spaceKey, { counts });
  }

  /**
   * Upserts the scan history entry for a space with the given status.
   */
  private upsertScanHistory(spaceKey: string, status: 'running' | 'completed' | 'failed'): void {
    const index = this.history().findIndex((h) => h.spaceKey === spaceKey);
    if (index >= 0) {
      const copy = [...this.history()];
      copy[index] = { ...copy[index], status };
      this.history.set(copy);
      return;
    }
    this.history.set([...this.history(), { spaceKey, status }]);
  }

  private append(line: string): void {
    const next = [...this.lines(), line];
    if (next.length > 1000) next.splice(0, next.length - 1000);
    this.lines.set(next);
  }

  // --- Progress helpers
  protected progressPercent(spaceKey: string | null | undefined): number {
    return this.scanProgressService.getProgressPercent(spaceKey);
  }

  private extractPercent(payload: RawStreamPayload): number | undefined {
    return this.scanProgressService.extractPercentFromPayload(payload);
  }

  private updateProgress(spaceKey: string, patch: Partial<{ total: number; index: number; percent: number }>): void {
    this.scanProgressService.updateProgress(spaceKey, patch);
  }
}
