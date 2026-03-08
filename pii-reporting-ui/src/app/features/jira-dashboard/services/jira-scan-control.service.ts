import { computed, inject, Injectable, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ConfirmationService } from 'primeng/api';
import { TranslocoService } from '@jsverse/transloco';
import { SentinelleApiService } from '../../../core/services/sentinelle-api.service';
import { ToastService } from '../../../core/services/toast.service';
import { ScanProgressService } from '../../../core/services/scan-progress.service';
import { JiraProjectsDashboardUtils } from '../jira-projects-dashboard.utils';
import { JiraPiiItemsStorageService } from './jira-pii-items-storage.service';
import { JiraDashboardUiStateService } from './jira-dashboard-ui-state.service';
import { JiraProjectDataManagementService } from './jira-project-data-management.service';
import { JiraSseEventHandlerService } from './jira-sse-event-handler.service';
import { StreamEventType } from '../../confluence-dashboard/spaces-dashboard-stream.utils';

@Injectable({
  providedIn: 'root'
})
export class JiraScanControlService {
  private readonly sentinelleApiService = inject(SentinelleApiService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly toastService = inject(ToastService);
  private readonly scanProgressService = inject(ScanProgressService);
  private readonly dashboardUtils = inject(JiraProjectsDashboardUtils);
  private readonly piiItemsStorage = inject(JiraPiiItemsStorageService);
  private readonly uiStateService = inject(JiraDashboardUiStateService);
  private readonly dataManagement = inject(JiraProjectDataManagementService);
  private readonly sseEventHandler = inject(JiraSseEventHandlerService);

  private sseSubscription?: Subscription;

  readonly isStreaming = signal(false);
  readonly isResuming = signal<boolean>(false);

  readonly canStartScan = computed(() =>
    !this.isStreaming() &&
    this.dataManagement.canStartScan()
  );

  readonly canResumeScan = computed(() =>
    !this.isStreaming() &&
    !this.dataManagement.isProjectsLoading() &&
    !!this.dataManagement.lastScanMeta() &&
    !this.isResuming()
  );

  startAll(): void {
    if (this.isStreaming()) return;

    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.globalScan.header'),
      message: this.translocoService.translate('confirmations.globalScan.message'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translocoService.translate('confirmations.globalScan.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.globalScan.rejectLabel'),
      acceptButtonStyleClass: 'p-button-info',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => this.executeStartAll(),
      reject: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.scanCancelled')
        );
      }
    });
  }

  startSelected(projectKeys: string[]): void {
    if (this.isStreaming() || projectKeys.length === 0) return;

    this.confirmationService.confirm({
      header: this.translocoService.translate('confirmations.selectedScan.header'),
      message: this.translocoService.translate('confirmations.selectedScan.message', { count: projectKeys.length }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.translocoService.translate('confirmations.selectedScan.acceptLabel'),
      rejectLabel: this.translocoService.translate('confirmations.selectedScan.rejectLabel'),
      acceptButtonStyleClass: 'p-button-info',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => this.executeStartSelected(projectKeys),
      reject: () => {
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.scanCancelled')
        );
      }
    });
  }

  private executeStartSelected(projectKeys: string[]): void {
    this.disconnectSse();
    this.resetDashboardForNewScan(projectKeys);
    this.isStreaming.set(true);

    this.sseSubscription = this.sentinelleApiService.startSelectedJiraProjectsStream(projectKeys).subscribe({
      next: (ev) => {
        this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
      },
      complete: () => {
        this.isStreaming.set(false);
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
  }

  private executeStartAll(): void {
    this.disconnectSse();
    this.resetDashboardForNewScan();

    this.isStreaming.set(true);

    this.sseSubscription = this.sentinelleApiService.startAllJiraProjectsStream().subscribe({
      next: (ev) => {
        this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
      },
      complete: () => {
        this.isStreaming.set(false);
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
  }

  private resetDashboardForNewScan(projectKeys?: string[]): void {
    if (projectKeys && projectKeys.length > 0) {
      for (const key of projectKeys) {
        this.piiItemsStorage.clearItemsForProject(key);
        this.scanProgressService.resetProgress(key);
        this.dashboardUtils.updateProject(key, {
          status: 'PENDING',
          lastScanTs: undefined,
          counts: { total: 0, high: 0, medium: 0, low: 0 }
        });
      }
    } else {
      this.piiItemsStorage.clearAllItems();
      this.scanProgressService.resetAllProgress();
      this.uiStateService.clearHistory();
      try {
        this.dashboardUtils.setProjects(this.dataManagement.projects());
      } catch {
        // no-op
      }
    }

    this.uiStateService.collapseAllRows();
    this.uiStateService.selectProject(null);
    this.uiStateService.activeProjectKey.set(null);
    this.toastService.clearScanErrors();
  }

  pauseScan(): void {
    const scanId = this.dataManagement.lastScanMeta()?.scanId;
    this.disconnectSse();

    if (scanId) {
      this.sentinelleApiService.pauseScan(scanId).subscribe({
        next: () => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.scanPaused', { scanId })
          );
        },
        error: (err) => {
          this.uiStateService.append(
            this.translocoService.translate('dashboard.logs.pauseError', {
              error: err?.message ?? err
            })
          );
        }
      });
    }
  }

  private disconnectSse(): void {
    if (this.sseSubscription) {
      this.sseSubscription.unsubscribe();
      this.sseSubscription = undefined;
    }
    this.isStreaming.set(false);
  }

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
        this.uiStateService.append(
          this.translocoService.translate('dashboard.logs.resumeAccepted')
        );

        this.isStreaming.set(true);
        this.sseSubscription = this.sentinelleApiService.startAllJiraProjectsStream(meta.scanId).subscribe({
          next: (ev) => {
            this.sseEventHandler.routeStreamEvent(ev.type as StreamEventType, ev.data);
          },
          complete: () => {
            this.isStreaming.set(false);
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

  reset(): void {
    this.disconnectSse();
    this.isResuming.set(false);
    this.uiStateService.activeProjectKey.set(null);
  }
}
