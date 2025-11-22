import {computed, inject, Injectable, signal} from '@angular/core';
import {Observable, Subscription} from 'rxjs';
import {
  LastScanMeta,
  SentinelleApiService,
  SpaceScanStateDto
} from '../../../core/services/sentinelle-api.service';
import {Space} from '../../../core/models/space';
import {SpaceUpdateInfo} from '../../../core/models/space-update-info.model';
import {
  ConfluenceSpacesPollingService
} from '../../../core/services/confluence-spaces-polling.service';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';
import {ScanProgressService} from '../../../core/services/scan-progress.service';
import {PiiItemsStorageService} from './pii-items-storage.service';
import {DashboardUiStateService} from './dashboard-ui-state.service';
import {TranslocoService} from '@jsverse/transloco';
import {coerceSpaceKey} from '../spaces-dashboard-stream.utils';

/**
 * Service responsible for managing space data loading and background polling.
 *
 * Business purpose:
 * - Loads Confluence spaces from backend cache for dashboard display
 * - Fetches last scan metadata and space statuses for resume functionality
 * - Loads persisted PII items to display after page refresh
 * - Provides manual refresh functionality for immediate updates
 * - Runs background polling to detect new spaces without disrupting workflow
 * - Detects spaces modified since last scan for re-scan indicators
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles data loading and polling orchestration
 * - Open/Closed: Can be extended with new data sources without modification
 * - Dependency Inversion: Depends on abstractions (API services, utils)
 *
 * Business Rules:
 * - Background polling starts after initial spaces load to prevent false positives
 * - Space statuses preserve real-time SSE data for active scans (RUNNING/PENDING/FAILED)
 * - Completed scans always use authoritative checkpoint progress (100%)
 * - Items are deduplicated by PiiItemsStorageService
 * - Counts recomputed after loading items to reflect current state
 */
@Injectable({
  providedIn: 'root'
})
export class SpaceDataManagementService {
  private readonly sentinelleApiService = inject(SentinelleApiService);
  private readonly pollingService = inject(ConfluenceSpacesPollingService);
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  private readonly scanProgressService = inject(ScanProgressService);
  private readonly piiItemsStorage = inject(PiiItemsStorageService);
  private readonly uiStateService = inject(DashboardUiStateService);
  private readonly translocoService = inject(TranslocoService);

  private pollingSub?: Subscription;

  // Spaces data
  readonly spaces = signal<Space[]>([]);
  readonly queue = signal<string[]>([]);
  readonly isSpacesLoading = signal<boolean>(true);

  // Last scan metadata for resume functionality
  readonly lastScanMeta = signal<LastScanMeta | null>(null);
  readonly lastSpaceStatuses = signal<SpaceScanStateDto[]>([]);

  // Manual refresh state
  readonly lastRefresh = signal<Date | null>(null);
  readonly isRefreshing = signal<boolean>(false);

  // New spaces notification state
  readonly hasNewSpaces = signal<boolean>(false);
  readonly newSpacesCount = signal<number>(0);

  // Space update info (modified since last scan)
  readonly spacesUpdateInfo = signal<SpaceUpdateInfo[]>([]);

  // Computed: whether scan can be started
  readonly canStartScan = computed(() =>
    !this.isSpacesLoading() && this.spaces().length > 0
  );

  /**
   * Fetches Confluence spaces from backend cache.
   * Business purpose: loads space list for dashboard display with instant response from DB cache.
   *
   * Side effects:
   * - Sets spaces() signal with loaded data
   * - Initializes queue with space keys
   * - Decorates spaces in SpacesDashboardUtils
   * - Re-applies last scan UI state if available
   * - Starts background polling after successful load
   */
  fetchSpaces(): Observable<void> {
    this.isSpacesLoading.set(true);
    this.isRefreshing.set(true);

    return new Observable(observer => {
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

          observer.next();
          observer.complete();
        },
        error: (e) => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.fetchSpacesError', {
              error: e?.message ?? e
            })
          );
          this.isSpacesLoading.set(false);
          this.isRefreshing.set(false);

          observer.error(e);
        }
      });
    });
  }

  /**
   * Loads last scan metadata for resume functionality.
   * Business purpose: enables Resume button and displays last scan info in dashboard.
   */
  loadLastScan(): Observable<void> {
    return new Observable(observer => {
      this.sentinelleApiService.getLastScanMeta().subscribe({
        next: (meta) => {
          this.lastScanMeta.set(meta);
          if (meta) {
            this.uiStateService.append(
              this.translocoService.translate('dashboard.logs.lastScanDetected', {
                scanId: meta.scanId,
                count: meta.spacesCount
              })
            );
          } else {
            this.uiStateService.append(
              this.translocoService.translate('dashboard.logs.noLastScan')
            );
          }
          observer.next();
          observer.complete();
        },
        error: () => {
          this.lastScanMeta.set(null);
          observer.complete();
        }
      });
    });
  }

  /**
   * Load dashboard summary using unified endpoint that combines authoritative progress
   * from checkpoints with aggregated counters from events.
   *
   * Business rule:
   * - If NO active scan: apply summary data to ALL spaces
   * - If scan IS active: apply summary data ONLY to COMPLETED spaces (preserve SSE real-time data for others)
   *
   * @param isActive Whether a scan is currently active (streaming SSE events)
   * @param alsoLoadItems Whether to also load persisted PII items after loading statuses
   */
  loadLastSpaceStatuses(isActive: boolean, alsoLoadItems: boolean = true): Observable<void> {
    return new Observable(observer => {
      this.sentinelleApiService.getDashboardSpacesSummary().subscribe({
        next: (summary) => {
          if (!summary) {
            this.lastSpaceStatuses.set([]);
            observer.next();
            observer.complete();
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
          this.piiItemsStorage.applyCountsFromItems();

          // Still load persisted items to display PII cards (if needed)
          // The unified endpoint provides progress and counters, but not the detailed PII items
          if (alsoLoadItems) {
            this.loadLastItems().subscribe({
              next: () => {
                observer.next();
                observer.complete();
              },
              error: () => {
                observer.next();
                observer.complete();
              }
            });
          } else {
            observer.next();
            observer.complete();
          }
        },
        error: () => {
          this.lastSpaceStatuses.set([]);
          observer.complete();
        }
      });
    });
  }

  /**
   * Computes UI status from backend scan state.
   * Business logic for status display based on scan state and activity.
   */
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

  /**
   * Loads persisted PII items from last scan.
   * Business purpose: backfill dashboard with PII items after page refresh or resume.
   *
   * Items are added via PiiItemsStorageService which handles deduplication.
   */
  private loadLastItems(): Observable<void> {
    return new Observable(observer => {
      this.sentinelleApiService.getLastScanItems().subscribe({
        next: (events) => {
          for (const event of events) {
            const type = (event as any)?.eventType as string | undefined;
            if (type !== 'item' && type !== 'attachmentItem') continue;

            const incomingKey = coerceSpaceKey(event);
            if (!incomingKey) continue;

            this.piiItemsStorage.addPiiItemToSpace(incomingKey, event);
          }

          // After adding items, compute counts for each space
          this.piiItemsStorage.applyCountsFromItems();

          observer.next();
          observer.complete();
        },
        error: () => {
          observer.complete();
        }
      });
    });
  }

  /**
   * Re-applies last known statuses and PII counts to UI spaces when the base list is (re)loaded.
   * Fixes race condition where loadLastScan() may finish before fetchSpaces(), causing badges not to refresh.
   */
  private reapplyLastScanUi(): void {
    const list = this.lastSpaceStatuses();
    if (!Array.isArray(list) || list.length === 0) {
      this.piiItemsStorage.applyCountsFromItems();
      return;
    }

    // isActive should be false during initial load, but we check anyway for safety
    const isActive = false;

    for (const s of list) {
      const uiStatus = this.computeUiStatus(s, isActive);
      this.spacesDashboardUtils.updateSpace(s.spaceKey, {
        status: uiStatus,
        lastScanTs: s.lastEventTs
      });

      if (s.status === 'COMPLETED') {
        this.scanProgressService.updateProgress(s.spaceKey, {
          percent: s.progressPercentage ?? 100
        });
      }
    }

    // Recompute counts from already loaded items (if any)
    this.piiItemsStorage.applyCountsFromItems();
  }

  /**
   * Manually refreshes the spaces list.
   * Business purpose: allows users to explicitly update the dashboard with latest cached data.
   */
  refreshSpaces(): void {
    this.fetchSpaces().subscribe();
    this.hasNewSpaces.set(false);
    this.newSpacesCount.set(0);
  }

  /**
   * Starts silent background polling to detect new spaces.
   * Business purpose: automatic discovery of new spaces without disrupting user workflow.
   *
   * Polling starts with the current count as baseline to prevent false positives.
   */
  startBackgroundPolling(): void {
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
   * Stops background polling.
   * Business purpose: cleanup on component destruction or manual stop.
   */
  stopBackgroundPolling(): void {
    this.pollingSub?.unsubscribe();
    this.pollingSub = undefined;
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
  loadSpacesUpdateInfo(): Observable<void> {
    return new Observable(observer => {
      this.sentinelleApiService.getSpacesUpdateInfo().subscribe({
        next: (updateInfos) => {
          this.spacesUpdateInfo.set(updateInfos);
          observer.next();
          observer.complete();
        },
        error: (err) => {
          console.error('[ui] Error loading spaces update info:', err);
          this.spacesUpdateInfo.set([]);
          observer.complete();
        }
      });
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
      const andMore = more > 0
        ? `\n${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.andMore', { count: more })}`
        : '';
      const list = `- ${shown.join('\n- ')}${andMore}`;
      parts.push(
        `${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.pagesModified')}\n${list}`
      );
    }

    const attachments = Array.isArray(info.updatedAttachments) ? info.updatedAttachments : [];
    if (attachments.length > 0) {
      const shown = attachments.slice(0, maxPerCategory);
      const more = attachments.length - shown.length;
      const andMore = more > 0
        ? `\n${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.andMore', { count: more })}`
        : '';
      const list = `- ${shown.join('\n- ')}${andMore}`;
      parts.push(
        `${this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.attachmentsModified')}\n${list}`
      );
    }

    // Fallback if no specific lists were provided by the backend
    if (parts.length === 0) {
      return this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.contentModified');
    }

    const currentLang = this.translocoService.getActiveLang();
    const locale = currentLang === 'en' ? 'en-US' : 'fr-FR';
    const dateStr = info.lastModified
      ? new Date(info.lastModified).toLocaleString(locale)
      : this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.updated', { date: 'Unknown' });
    const updatedLabel = info.lastModified
      ? this.translocoService.translate('dashboard.notifications.spaceUpdated.tooltip.updated', { date: dateStr })
      : dateStr;

    return updatedLabel + '\n' + parts.join('\n\n');
  }

  /**
   * Resets all data state to initial values.
   * Business purpose: clean slate for new scan or full dashboard reset.
   */
  reset(): void {
    this.spaces.set([]);
    this.queue.set([]);
    this.lastScanMeta.set(null);
    this.lastSpaceStatuses.set([]);
    this.hasNewSpaces.set(false);
    this.newSpacesCount.set(0);
    this.spacesUpdateInfo.set([]);
    this.lastRefresh.set(null);
  }
}
