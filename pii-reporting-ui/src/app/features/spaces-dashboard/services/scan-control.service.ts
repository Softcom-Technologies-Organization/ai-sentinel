import {computed, inject, Injectable, signal} from '@angular/core';
import {Subscription} from 'rxjs';
import {ConfirmationService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {SentinelleApiService} from '../../../core/services/sentinelle-api.service';
import {ToastService} from '../../../core/services/toast.service';
import {ScanProgressService} from '../../../core/services/scan-progress.service';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';
import {PiiItemsStorageService} from './pii-items-storage.service';
import {DashboardUiStateService} from './dashboard-ui-state.service';
import {SpaceDataManagementService} from './space-data-management.service';
import {SseEventHandlerService} from './sse-event-handler.service';
import {StreamEventType} from '../spaces-dashboard-stream.utils';

/**
 * Service responsible for controlling scan lifecycle (start, stop, resume).
 *
 * Business purpose:
 * - Orchestrates multi-space scan initiation with user confirmation
 * - Manages SSE stream subscription for real-time scan updates
 * - Handles scan pause/stop functionality
 * - Enables scan resume from last checkpoint
 * - Coordinates state reset for new scans
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles scan control and SSE subscription
 * - Open/Closed: Can be extended with new scan types without modification
 * - Dependency Inversion: Depends on abstractions (services, utils)
 *
 * Business Rules:
 * - Global scan requires user confirmation via dialog
 * - Starting new scan purges all previous scan data
 * - Resuming scan preserves existing results and continues from checkpoint
 * - Stopping scan marks it as PAUSED in backend for later resume
 * - SSE subscription managed centrally to prevent memory leaks
 */
@Injectable({
  providedIn: 'root'
})
export class ScanControlService {
  private readonly sentinelleApiService = inject(SentinelleApiService);
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

  // Streaming state
  readonly isStreaming = signal(false);
  readonly isResuming = signal<boolean>(false);

  // Computed: whether scan can be started
  readonly canStartScan = computed(() =>
    !this.isStreaming() &&
    this.dataManagement.canStartScan()
  );

  // Computed: whether scan can be resumed
  readonly canResumeScan = computed(() =>
    !this.isStreaming() &&
    !this.dataManagement.isSpacesLoading() &&
    !!this.dataManagement.lastScanMeta() &&
    !this.isResuming()
  );

  /**
   * Initiates a global scan of all spaces with user confirmation.
   * Business purpose: ensures user explicitly approves full scan before data purge.
   *
   * Flow:
   * 1. Check if already streaming (early return if yes)
   * 2. Display confirmation dialog with translated messages
   * 3. On accept: execute scan via executeStartAll()
   * 4. On reject: log cancellation
   */
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
      // Use the same blue style as the Play button on the dashboard
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
   * Executes the actual scan start after user confirmation.
   * Business purpose: initiates full multi-space scan with data purge.
   *
   * Flow:
   * 1. Stop any existing scan
   * 2. Reset dashboard for clean slate
   * 3. Purge all previous scan data from backend
   * 4. Open SSE stream for real-time updates
   * 5. Subscribe to stream events (delegated to callback)
   */
  private executeStartAll(): void {
    this.disconnectSse();

    // Clear previous dashboard results before starting a brand-new scan
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

        // Open SSE stream - events routed to event handler service
        this.sseSubscription = this.sentinelleApiService.startAllSpacesStream().subscribe({
          next: (ev) => {
            this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
          },
          error: (err) => {
            this.uiStateService.append(
              this.translocoService.translate('dashboard.logs.sseError', {
                error: err?.message ?? err
              })
            );
            this.isStreaming.set(false);
          }
        });
      },
      error: (err) => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.purgeError', {
            error: err?.message ?? err
          })
        );
        this.isStreaming.set(false);
      }
    });
  }

  /**
   * Resets dashboard state and UI decoration when starting a completely new scan.
   * Business rule: do not apply this on resume, as resume should continue displaying existing results.
   *
   * Side effects:
   * - Clears in-memory PII items
   * - Resets all progress indicators
   * - Clears scan history
   * - Collapses all rows and resets selection
   * - Clears previous error toasts
   * - Reinitializes UI decoration for all spaces
   */
  private resetDashboardForNewScan(): void {
    // Clear in-memory per-space items and progress
    this.piiItemsStorage.clearAllItems();
    this.scanProgressService.resetAllProgress();
    this.uiStateService.clearHistory();

    // Collapse all rows and reset selection/active markers
    this.uiStateService.collapseAllRows();
    this.uiStateService.selectSpace(null);
    this.uiStateService.activeSpaceKey.set(null);

    // Clear previous error toasts when starting a new scan
    this.toastService.clearScanErrors();

    // Reinitialize UI decoration (status/counts/lastScanTs) for all known spaces
    try {
      this.spacesDashboardUtils.setSpaces(this.dataManagement.spaces());
    } catch {
      // no-op: defensive in case spaces() not ready yet
    }
  }

  /**
   * Pauses the current scan by disconnecting SSE and marking backend as PAUSED.
   * Business purpose: User explicitly wants to interrupt scan for later resume.
   *
   * Flow:
   * 1. Disconnect SSE stream
   * 2. Mark backend as PAUSED for resume capability
   * 3. Reload statuses and scan metadata
   */
  pauseScan(): void {
    const scanId = this.dataManagement.lastScanMeta()?.scanId;

    // Disconnect SSE stream
    this.disconnectSse();

    // Mark backend as PAUSED for resume capability
    if (scanId) {
      this.sentinelleApiService.pauseScan(scanId).subscribe({
        next: () => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.scanPaused', { scanId })
          );
          this.dataManagement.loadLastSpaceStatuses(false, false).subscribe();
          this.dataManagement.loadLastScan().subscribe();
        },
        error: (err) => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.pauseError', {
              error: err?.message ?? err
            })
          );
          // Still reload statuses even if pause fails
          this.dataManagement.loadLastSpaceStatuses(false, false).subscribe();
          this.dataManagement.loadLastScan().subscribe();
        }
      });
    } else {
      // No scanId, just reload statuses
      this.dataManagement.loadLastSpaceStatuses(false, false).subscribe();
      this.dataManagement.loadLastScan().subscribe();
    }
  }

  /**
   * Disconnects SSE stream without marking backend as PAUSED.
   * Business purpose: Clean SSE teardown during browser close or component destruction.
   * Backend scan continues running independently.
   *
   * CRITICAL: Does NOT call backend pauseScan() - scan continues in background.
   */
  private disconnectSse(): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
      this.sseSubscription = undefined;
    }
    this.isStreaming.set(false);
  }

  /**
   * Resumes a paused scan from its last checkpoint.
   * Business purpose: allows continuation of interrupted scans without losing progress.
   *
   * Flow:
   * 1. Validate prerequisites (has lastScanMeta, not already streaming/resuming)
   * 2. Set resuming state to true (prevents double-clicks)
   * 3. Call backend to resume scan
   * 4. Immediately reconnect SSE stream for real-time updates
   * 5. Load last statuses and items to backfill gap during disconnection
   */
  resumeLastScan(): void {
    const meta = this.dataManagement.lastScanMeta();
    if (!meta || this.isStreaming() || this.isResuming()) {
      return;
    }

    this.isResuming.set(true);
    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.resumeRequest', {
        scanId: meta.scanId
      })
    );

    this.sentinelleApiService.resumeScan(meta.scanId).subscribe({
      next: () => {
        this.isResuming.set(false);

        // Immediately reconnect to the SSE stream so new WebFlux events are displayed live
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.resumeAccepted')
        );

        // Ensure no leftover subscription then open the stream
        this.isStreaming.set(true);
        this.sseSubscription = this.sentinelleApiService.startAllSpacesStream(meta.scanId).subscribe({
          next: (ev) => {
            this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
          },
          error: (err) => {
            this.uiStateService.append(
              this.translocoService.translate('dashboard.logs.sseError', {
                error: err?.message ?? err
              })
            );
            this.isStreaming.set(false);
          }
        });

        // Also refresh statuses and backfill persisted items to cover the gap while SSE was disconnected.
        // Duplicates are avoided by PiiItemsStorageService via page_id + attachmentName dedup.
        this.dataManagement.loadLastSpaceStatuses(true, true).subscribe();
      },
      error: (e) => {
        this.isResuming.set(false);
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.resumeError', {
            error: e?.message ?? e
          })
        );
      }
    });
  }


  /**
   * Checks if a scan is currently running on backend and reconnects SSE stream if needed.
   * Business purpose: automatic reconnection after page refresh or browser tab reopen.
   *
   * This method is called during dashboard initialization to detect orphaned running scans
   * and automatically reattach the SSE stream without user interaction.
   *
   * Detection logic:
   * 1. Verify lastScanMeta exists with valid scanId
   * 2. Check if any space has RUNNING status in lastSpaceStatuses
   * 3. Ensure no space has PAUSED status (user explicitly paused scan)
   * 4. If conditions met: reconnect to SSE stream with scanId
   *
   * Flow:
   * 1. Early return if already streaming (avoid double connection)
   * 2. Get lastScanMeta and lastSpaceStatuses from dataManagement
   * 3. Detect RUNNING spaces and check for PAUSED status
   * 4. If scan is running AND not paused: log reconnection attempt and open SSE stream
   * 5. Subscribe to SSE events (delegated to sseEventHandler)
   * 6. Reload statuses to backfill gap during disconnection
   *
   * Business Rule: Only reconnect if scan is actively RUNNING, not if user explicitly PAUSED it.
   */
  checkAndReconnectToRunningScan(): void {
    // Avoid double connection if already streaming
    if (this.isStreaming()) {
      return;
    }

    const meta = this.dataManagement.lastScanMeta();
    const statuses = this.dataManagement.lastSpaceStatuses();

    // No scan metadata available
    if (!meta?.scanId) {
      return;
    }

    // Only reconnect if at least one space is RUNNING
    // AND no space is PAUSED (user explicitly paused the scan)
    const hasRunningScan = statuses.some(s => s.status === 'RUNNING');
    const hasPausedScan = statuses.some(s => s.status === 'PAUSED');

    if (!hasRunningScan || hasPausedScan) {
      return;
    }

    // Scan is running on backend, reconnect SSE stream automatically
    this.uiStateService.append(
      this.translocoService.translate('dashboard.logs.autoReconnect', {
        scanId: meta.scanId
      })
    );

    this.isStreaming.set(true);
    this.sseSubscription = this.sentinelleApiService.startAllSpacesStream(meta.scanId).subscribe({
      next: (ev) => {
        this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
      },
      error: (err) => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.sseError', {
            error: err?.message ?? err
          })
        );
        this.isStreaming.set(false);
      }
    });

    // Reload statuses to backfill any events missed during disconnection
    // The replay buffer on backend will also provide recent events
    this.dataManagement.loadLastSpaceStatuses(true, true).subscribe();
  }

  /**
   * Resets all scan control state to initial values.
   * Business purpose: cleanup for component destruction or full reset.
   *
   * CRITICAL: Only disconnects SSE, does NOT pause backend scan.
   * Scan continues running in background after component destruction.
   */
  reset(): void {
    this.disconnectSse();
    this.isResuming.set(false);
    this.uiStateService.activeSpaceKey.set(null);
  }
}
