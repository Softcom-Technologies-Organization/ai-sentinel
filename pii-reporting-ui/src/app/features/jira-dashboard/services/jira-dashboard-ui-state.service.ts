import { computed, inject, Injectable, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { HistoryEntry } from '../../../core/models/history-entry';
import { JiraProjectsDashboardUtils } from '../jira-projects-dashboard.utils';

@Injectable({
  providedIn: 'root'
})
export class JiraDashboardUiStateService {
  private readonly translocoService = inject(TranslocoService);
  private readonly dashboardUtils = inject(JiraProjectsDashboardUtils);

  readonly expandedRowKeys = signal<Record<string, boolean>>({});
  readonly selectedProjectKey = signal<string | null>(null);
  readonly selectedProjects = signal<{ key: string }[]>([]);
  readonly activeProjectKey = signal<string | null>(null);
  readonly maskByDefault = signal(true);
  readonly lines = signal<string[]>([]);
  readonly history = signal<HistoryEntry[]>([]);

  readonly selectedProjectsCount = computed(() => {
    return this.selectedProjects().length;
  });

  onRowExpand(event: { data?: { key?: string } }): void {
    const key = event?.data?.key;
    if (!key) return;
    const map = this.expandedRowKeys();
    if (!map[key]) {
      this.expandedRowKeys.set({ ...map, [key]: true });
    }
    this.selectedProjectKey.set(key);
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

  collapseAllRows(): void {
    this.expandedRowKeys.set({});
  }

  selectProject(projectKey: string | null): void {
    this.selectedProjectKey.set(projectKey);
  }

  append(line: string): void {
    const next = [...this.lines(), line];
    if (next.length > 1000) {
      next.splice(0, next.length - 1000);
    }
    this.lines.set(next);
  }

  clearHistory(): void {
    this.history.set([]);
  }

  upsertScanHistory(projectKey: string, status: 'running' | 'completed' | 'failed'): void {
    const index = this.history().findIndex((h) => h.spaceKey === projectKey);
    if (index >= 0) {
      const copy = [...this.history()];
      copy[index] = { ...copy[index], status };
      this.history.set(copy);
      return;
    }
    this.history.set([...this.history(), { spaceKey: projectKey, status }]);
  }

  statusLabel(status?: string): string {
    return this.translocoService.translate(this.getStatusKey(status));
  }

  private getStatusKey(status?: string): string {
    switch (status?.toUpperCase()) {
      case 'RUNNING': return 'dashboard.status.running';
      case 'OK': return 'dashboard.status.ok';
      case 'FAILED': return 'dashboard.status.failed';
      case 'PENDING': return 'dashboard.status.pending';
      case 'PAUSED': return 'dashboard.status.paused';
      default: return 'dashboard.status.pending';
    }
  }

  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    if (status === 'FAILED') return 'danger';
    if (status === 'RUNNING') return 'warning';
    if (status === 'PAUSED') return 'info';
    if (status === 'PENDING' || !status) return 'secondary';
    return 'success';
  }

  statusStyleClass(status?: string): string | undefined {
    if (status === 'RUNNING') return 'status-running';
    if (status === 'PAUSED') return 'status-paused';
    return undefined;
  }

  reset(): void {
    this.expandedRowKeys.set({});
    this.selectedProjectKey.set(null);
    this.activeProjectKey.set(null);
    this.selectedProjects.set([]);
    this.lines.set([]);
    this.history.set([]);
  }
}
