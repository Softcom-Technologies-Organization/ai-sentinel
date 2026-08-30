import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { take } from 'rxjs/operators';
import { ConfirmationService } from 'primeng/api';
import { TranslocoService } from '@jsverse/transloco';
import { SentinelleApiService } from '../../../core/services/sentinelle-api.service';
import { ScanStatusPollingService } from '../../../core/services/scan-status-polling.service';
import { ToastService } from '../../../core/services/toast.service';
import { ScanProgressService } from '../../../core/services/scan-progress.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';
import { PiiItemsStorageService } from './pii-items-storage.service';
import { DashboardUiStateService } from './dashboard-ui-state.service';
import { SpaceDataManagementService } from './space-data-management.service';
import { SseEventHandlerService } from './sse-event-handler.service';
import { StreamEventType } from '../spaces-dashboard-stream.utils';
import { StreamEvent } from '../../../core/models/stream-event';

/**
 * Service responsible for controlling scan lifecycle (start, stop, resume).
 *
 * Business purpose:
 * - Orchestrates multi-space scan initiation with user confirmation
 * - Manages SSE stream subscription for real-time PII item updates
 * - Manages polling for backend-authoritative scan statuses
 * - Handles scan pause/stop functionality
 * - Enables scan resume from last checkpoint
 * - Coordinates state reset for new scans
 *
 * Architecture:
 * - Statuses/progress: polled via ScanStatusPollingService (single source of truth)
 * - PII items: streamed via SSE (live cards)
 * - Completion detection: via scanCompleted$ from polling (not SSE multiComplete)
 */
@Injectable({
  providedIn: 'root'
})
export class ScanControlService {
  private readonly sentinelleApiService = inject(SentinelleApiService);
  private readonly statusPolling = inject(ScanStatusPollingService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly toastService = inject(ToastService);
  private readonly scanProgressService = inject(ScanProgressService);
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  private readonly piiItemsStorage = inject(PiiItemsStorageService);
  private readonly uiStateService = inject(DashboardUiStateService);
  private readonly dataManagement = inject(SpaceDataManagementService);
  private readonly sseEventHandler = inject(SseEventHandlerService);

  private sseSubscription?: Subscription;
  private completionSub?: Subscription;

  // Streaming state (SSE connected for live items)
  readonly isStreaming = signal(false);

  // Backend-authoritative button states (derived from polling)
  readonly scanActive = this.statusPolling.scanActive;
  readonly scanPaused = this.statusPolling.scanPaused;
  readonly actionPending = this.statusPolling.actionPending;

  // Computed: whether scan can be started (also available when paused — starts fresh scan)
  readonly canStartScan = computed(() =>
    !this.actionPending() &&
    !this.scanActive() &&
    this.dataManagement.canStartScan()
  );

  // Computed: whether scan can be paused
  readonly canPauseScan = computed(() =>
    !this.actionPending() &&
    this.scanActive()
  );

  // Computed: whether scan can be resumed
  readonly canResumeScan = computed(() =>
    !this.actionPending() &&
    this.scanPaused() &&
    !!this.dataManagement.lastScanMeta()
  );

  // Computed: whether data can be purged (not during active scan or pending action)
  readonly canPurgeData = computed(() =>
    !this.actionPending() &&
    !this.scanActive()
  );

  /**
   * Initiates a global scan of all spaces with user confirmation.
   * Business purpose: ensures user explicitly approves full scan before data purge.
   */
  startAll(): void {
    if (this.scanActive() || this.actionPending()) {
      return;
    }

    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.globalScan.header'),
      message: this.translocoService.translate('confirmations.globalScan.message'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translocoService.translate('confirmations.globalScan.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.globalScan.rejectLabel'),
      acceptButtonStyleClass: 'p-button-info',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => {
        this.executeStartAll();
      },
      reject: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.scanCancelled')
        );
      }
    });
  }

  /**
   * Initiates a scan for selected spaces with user confirmation.
   */
  startSelected(spaceKeys: string[]): void {
    if (this.scanActive() || this.actionPending() || spaceKeys.length === 0) {
      return;
    }

    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.selectedScan.header'),
      message: this.translocoService.translate('confirmations.selectedScan.message', { count: spaceKeys.length }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translocoService.translate('confirmations.selectedScan.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.selectedScan.rejectLabel'),
      acceptButtonStyleClass: 'p-button-info',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => {
        this.executeStartSelected(spaceKeys);
      },
      reject: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.scanCancelled')
        );
      }
    });
  }

  private executeStartSelected(spaceKeys: string[]): void {
    this.statusPolling.actionPending.set(true);
    this.disconnectSse();
    this.dataManagement.currentScanSpaceKeys.set(spaceKeys);
    this.resetDashboardForNewScan(spaceKeys);

    this.isStreaming.set(true);
    this.initializeOptimisticUi(spaceKeys);
    // Subscribe SSE first to trigger the scan on the backend before polling starts
    this.subscribeSse(this.sentinelleApiService.startSelectedSpacesStream(spaceKeys));
    this.startPollingWithCompletionDetection();
    // actionPending stays true until polling detects RUNNING (auto-cleared in applySummary)
  }

  private executeStartAll(): void {
    this.statusPolling.actionPending.set(true);
    this.disconnectSse();
    this.dataManagement.currentScanSpaceKeys.set(null);

    this.resetDashboardForNewScan();

    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.purging')
    );

    this.sentinelleApiService.purgeAllScans().subscribe({
      next: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeOk')
        );
        this.isStreaming.set(true);
        this.initializeOptimisticUi();
        // Subscribe SSE first to trigger the scan on the backend before polling starts
        this.subscribeSse(this.sentinelleApiService.startAllSpacesStream());
        this.startPollingWithCompletionDetection();
        // actionPending stays true until polling detects RUNNING (auto-cleared in applySummary)
      },
      error: (err) => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeError', {
            error: err?.message ?? err
          })
        );
        this.isStreaming.set(false);
        this.statusPolling.actionPending.set(false);
      }
    });
  }

  /**
   * Starts polling and subscribes to completion signal.
   * When all spaces are COMPLETED/FAILED, triggers onScanComplete().
   */
  private startPollingWithCompletionDetection(): void {
    this.statusPolling.start(3000);

    this.completionSub?.unsubscribe();
    this.completionSub = this.statusPolling.scanCompleted$.pipe(
      take(1)
    ).subscribe(() => {
      this.onScanComplete();
    });
  }

  /**
   * Optimistic UI initialization at scan start.
   * Sets spaces to PENDING immediately so the user gets instant feedback.
   * (The first poll will update with actual statuses within ~0ms)
   */
  private initializeOptimisticUi(spaceKeys?: string[]): void {
    const spaces = this.dataManagement.spaces();
    const scopeKeys = spaceKeys
      ? new Set(spaceKeys.map(k => k.trim().toLowerCase()))
      : null;

    const queueKeys = scopeKeys
      ? spaces.filter(s => scopeKeys.has(s.key.trim().toLowerCase())).map(s => s.key)
      : spaces.map(s => s.key);

    this.dataManagement.queue.set(queueKeys);

    for (const space of spaces) {
      if (!scopeKeys || scopeKeys.has(space.key.trim().toLowerCase())) {
        this.spacesDashboardUtils.updateSpace(space.key, { status: 'PENDING' });
      }
    }

    if (!this.uiStateService.selectedSpaceKey() && queueKeys.length > 0) {
      this.uiStateService.selectedSpaceKey.set(queueKeys[0]);
    }
  }

  /**
   * Resets dashboard state and UI decoration when starting a new scan.
   * Business rule: do not apply this on resume, as resume should continue displaying existing results.
   */
  private resetDashboardForNewScan(spaceKeys?: string[]): void {
    if (spaceKeys && spaceKeys.length > 0) {
      for (const key of spaceKeys) {
        this.piiItemsStorage.clearItemsForSpace(key);
        this.scanProgressService.resetProgress(key);
        this.spacesDashboardUtils.updateSpace(key, {
          status: 'PENDING',
          lastScanTs: undefined,
          counts: { total: 0, high: 0, medium: 0, low: 0 }
        });
      }

      // Reconcile stale queue badges: spaces left PENDING by a previous scan but
      // excluded from this new selection must fall back to NOT_STARTED. Other
      // statuses (PAUSED/OK/FAILED/RUNNING) are preserved.
      const nk = (v: string | null | undefined) => String(v ?? '').trim().toLowerCase();
      const selection = new Set(spaceKeys.map(nk));
      for (const space of this.spacesDashboardUtils.allSpaces()) {
        if (!selection.has(nk(space.key)) && space.status === 'PENDING') {
          this.spacesDashboardUtils.updateSpace(space.key, { status: 'NOT_STARTED' });
        }
      }
    } else {
      this.piiItemsStorage.clearAllItems();
      this.scanProgressService.resetAllProgress();
      this.uiStateService.clearHistory();

      try {
        this.spacesDashboardUtils.setSpaces(this.dataManagement.spaces());
      } catch {
        // no-op
      }
    }

    this.uiStateService.collapseAllRows();
    this.uiStateService.selectSpace(null);
    this.uiStateService.activeSpaceKey.set(null);

    this.toastService.clearScanErrors();
  }

  /**
   * Pauses the current scan. Shows spinner, calls backend, then fetches fresh state.
   */
  pauseScan(): void {
    const scanId = this.dataManagement.lastScanMeta()?.scanId;
    this.statusPolling.actionPending.set(true);
    this.statusPolling.stop();
    this.completionSub?.unsubscribe();
    this.disconnectSse();

    if (scanId) {
      this.sentinelleApiService.pauseScan(scanId).subscribe({
        next: () => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.scanPaused', { scanId })
          );
          this.refreshAfterAction();
        },
        error: (err) => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.pauseError', {
              error: err?.message ?? err
            })
          );
          this.refreshAfterAction();
        }
      });
    } else {
      this.refreshAfterAction();
    }
  }

  /**
   * After an action (pause/resume), immediately fetch backend state to update button panel.
   */
  private refreshAfterAction(): void {
    this.statusPolling.forceRefresh().then(() => {
      this.statusPolling.actionPending.set(false);
    });
  }

  private disconnectSse(): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
      this.sseSubscription = undefined;
    }
    this.isStreaming.set(false);
  }

  /**
   * Subscribes to an SSE stream for live PII items.
   * SSE error is non-fatal: items won't appear live but statuses still update via polling.
   */
  private subscribeSse(stream$: Observable<StreamEvent>): void {
    this.sseSubscription = stream$.subscribe({
      next: (ev) => {
        this.releaseOptimisticUiIfScanStopped(ev);
        this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
      },
      error: (err) => {
        // SSE error is non-fatal: items won't appear live but statuses still update via polling
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.sseError', {
            error: err?.message ?? err
          })
        );
        this.sseSubscription?.unsubscribe();
        this.sseSubscription = undefined;
        this.isStreaming.set(false);
      }
    });
  }

  /**
   * Releases the optimistic "scan starting" state when the backend says the scan is not running.
   *
   * Business purpose: pressing Start sets the spaces to PENDING and starts polling, on the
   * assumption that the scan will report RUNNING shortly. A scan refused before it began — an
   * enabled detector unreachable, LM Studio closed — never reports RUNNING, so without this the
   * panel stays on "SCAN EN COURS" forever with Start disabled, and the operator believes a scan
   * is running while nothing happens.
   *
   * Two distinct outcomes:
   * - refused before starting: polling is stopped, because as long as it runs with no space
   *   RUNNING or PAUSED the panel keeps reporting a scan in progress (see isTransitioning);
   * - paused mid-scan: polling is kept, since it is what reads the PAUSED status back and turns
   *   the Resume button on.
   */
  private releaseOptimisticUiIfScanStopped(event: StreamEvent): void {
    if (event.type !== 'scanError') {
      return;
    }
    const errorKey = (event.data as Record<string, unknown> | undefined)?.['errorKey'] as string | undefined;
    const refusedBeforeStarting = this.toastService.isScanRefused(errorKey);
    if (!refusedBeforeStarting && !this.toastService.isScanPaused(errorKey)) {
      return;
    }

    this.statusPolling.actionPending.set(false);
    this.isStreaming.set(false);
    if (refusedBeforeStarting) {
      this.statusPolling.stop();
      this.disconnectSse();
      this.dataManagement.queue.set([]);
    }
    // Reapplies the backend statuses over the optimistic PENDING ones.
    void this.statusPolling.forceRefresh();
  }

  /**
   * Handles scan completion: stops polling, disconnects SSE and reloads final statuses.
   */
  private onScanComplete(): void {
    this.statusPolling.stop();
    this.completionSub?.unsubscribe();
    this.disconnectSse();
    this.dataManagement.currentScanSpaceKeys.set(null);
    // Final reload to ensure severity counts are exact
    this.dataManagement.loadLastSpaceStatuses().subscribe();
    this.dataManagement.loadLastScan().subscribe();
  }

  /**
   * Resumes a paused scan. Shows spinner, calls backend, fetches fresh state, reconnects SSE.
   */
  resumeLastScan(): void {
    const meta = this.dataManagement.lastScanMeta();
    if (!meta || this.actionPending()) return;

    this.statusPolling.actionPending.set(true);
    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.resumeRequest', { scanId: meta.scanId })
    );

    this.sentinelleApiService.resumeScan(meta.scanId).subscribe({
      next: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.resumeAccepted')
        );
        this.isStreaming.set(true);
        this.startPollingWithCompletionDetection();
        this.subscribeSse(this.sentinelleApiService.startAllSpacesStream(meta.scanId));
        this.dataManagement.loadLastItems().subscribe();
        // forceRefresh clears actionPending via applySummary setting scanActive=true
        this.statusPolling.forceRefresh().then(() => {
          this.statusPolling.actionPending.set(false);
        });
      },
      error: (e) => {
        this.statusPolling.actionPending.set(false);
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.resumeError', { error: e?.message ?? e })
        );
      }
    });
  }

  /**
   * Initializes button panel state and reconnects to running scan if needed.
   * Called during dashboard initialization after loading statuses.
   *
   * Always syncs button panel signals (scanActive, scanPaused) from backend.
   *
   * Scope restoration: when the current scan scope is unknown (e.g. after a page
   * reload), it is rebuilt from the spaces whose checkpoint carries the current
   * scanId. This runs even when the scan is paused, so resume and "En attente"
   * badges stay correct after a reload.
   *
   * Reconnection gate (bounded to the current scan): reconnect only when the scan
   * is unfinished — a space RUNNING or NOT_STARTED (queued via upfront checkpoints)
   * under the current scanId means the scan is still in progress, even in the
   * inter-space window where no space is RUNNING at the instant of the reload.
   * A finished scan (all COMPLETED/FAILED/INTERRUPTED) does not reconnect; a paused
   * scan never resumes on its own.
   */
  reconnectIfScanRunning(): void {
    // Always sync button panel state from backend — regardless of scan state
    this.statusPolling.forceRefresh();

    if (this.isStreaming()) return;

    const meta = this.dataManagement.lastScanMeta();
    const statuses = this.dataManagement.lastSpaceStatuses();

    if (!meta?.scanId) return;

    if (this.dataManagement.currentScanSpaceKeys() === null) {
      const scopeKeys = statuses
        .filter(s => s.scanId === meta.scanId)
        .map(s => s.spaceKey);
      if (scopeKeys.length > 0) {
        this.dataManagement.currentScanSpaceKeys.set(scopeKeys);
      }
    }

    // Keep entries without scanId for backward compatibility with pre-fix scans.
    const scanStatuses = statuses.filter(s => !s.scanId || s.scanId === meta.scanId);
    const hasPaused = scanStatuses.some(s => s.status === 'PAUSED');
    const hasUnfinished = scanStatuses.some(s => s.status === 'RUNNING' || s.status === 'NOT_STARTED');

    if (hasPaused || !hasUnfinished) return;

    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.autoReconnect', { scanId: meta.scanId })
    );

    this.isStreaming.set(true);
    this.startPollingWithCompletionDetection();
    this.subscribeSse(this.sentinelleApiService.startAllSpacesStream(meta.scanId));
  }

  /**
   * Purges all scan data after user confirmation.
   * Business purpose: allows user to manually delete all scan results without starting a new scan.
   */
  purgeAllData(): void {
    if (this.scanActive() || this.actionPending()) {
      return;
    }

    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.purgeData.header'),
      message: this.translocoService.translate('confirmations.purgeData.message'),
      icon: 'pi pi-trash',
      acceptLabel: this.translocoService.translate('confirmations.purgeData.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.purgeData.rejectLabel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => {
        this.executePurgeAllData();
      },
      reject: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeManualCancelled')
        );
      }
    });
  }

  private executePurgeAllData(): void {
    this.statusPolling.actionPending.set(true);

    this.sentinelleApiService.purgeAllScans().subscribe({
      next: () => {
        this.piiItemsStorage.clearAllItems();
        this.scanProgressService.resetAllProgress();
        this.uiStateService.clearHistory();
        this.uiStateService.collapseAllRows();
        this.uiStateService.selectSpace(null);
        this.uiStateService.activeSpaceKey.set(null);

        try {
          this.spacesDashboardUtils.setSpaces(this.dataManagement.spaces());
        } catch {
          // no-op
        }

        this.toastService.clearScanErrors();
        this.statusPolling.actionPending.set(false);

        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeManualOk')
        );

        this.dataManagement.loadLastScan().subscribe();
        this.statusPolling.forceRefresh();
      },
      error: (err) => {
        this.statusPolling.actionPending.set(false);
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeManualError', {
            error: err?.message ?? err
          })
        );
      }
    });
  }

  /**
   * Resets all scan control state to initial values.
   */
  reset(): void {
    this.statusPolling.stop();
    this.completionSub?.unsubscribe();
    this.disconnectSse();
    this.statusPolling.actionPending.set(false);
    this.uiStateService.activeSpaceKey.set(null);
  }
}
