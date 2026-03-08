import {
    ChangeDetectionStrategy,
    Component,
    computed,
    DestroyRef,
    EventEmitter,
    inject,
    OnDestroy,
    OnInit,
    Output,
    signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TranslocoModule } from '@jsverse/transloco';
import { PiiPageCardComponent } from '../pii-page-card/pii-page-card.component';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { BadgeModule } from 'primeng/badge';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Ripple } from 'primeng/ripple';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { ScanProgressBarComponent } from '../../shared/components/scan-progress-bar/scan-progress-bar.component';
import { ScanControlsComponent } from '../../shared/components/scan-controls/scan-controls.component';
import { SortEvent } from 'primeng/api';
import { PiiHelpDialogComponent } from '../../shared/components/pii-help-dialog/pii-help-dialog.component';
import { SeverityCardsComponent } from '../severity-cards/severity-cards.component';
import { SeverityCounts } from '../../core/models/severity-counts';
import { JiraConnectionConfigService } from '../../core/services/jira-connection-config.service';
import { JiraProjectFilteringService } from './services/jira-project-filtering.service';
import { JiraDashboardUiStateService } from './services/jira-dashboard-ui-state.service';
import { JiraPiiItemsStorageService } from './services/jira-pii-items-storage.service';
import { JiraProjectDataManagementService } from './services/jira-project-data-management.service';
import { JiraScanControlService } from './services/jira-scan-control.service';
import { JiraProjectsDashboardUtils } from './jira-projects-dashboard.utils';

@Component({
  selector: 'app-jira-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    ToggleButtonModule,
    PiiPageCardComponent,
    BadgeModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    Ripple,
    TooltipModule,
    SkeletonModule,
    PiiHelpDialogComponent,
    TranslocoModule,
    ScanProgressBarComponent,
    ScanControlsComponent,
    SeverityCardsComponent
  ],
  templateUrl: './jira-dashboard.component.html',
  styleUrl: './jira-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JiraDashboardComponent implements OnInit, OnDestroy {
  private readonly filteringService = inject(JiraProjectFilteringService);
  private readonly uiStateService = inject(JiraDashboardUiStateService);
  private readonly piiItemsStorage = inject(JiraPiiItemsStorageService);
  private readonly dataManagement = inject(JiraProjectDataManagementService);
  private readonly scanControl = inject(JiraScanControlService);
  private readonly jiraConfigService = inject(JiraConnectionConfigService);
  private readonly destroyRef = inject(DestroyRef);
  readonly dashboardUtils = inject(JiraProjectsDashboardUtils);

  @Output() openSettings = new EventEmitter<number>();

  readonly skeletonRows: number[] = Array.from({ length: 10 }, (_, i) => i);
  readonly showPiiHelpDialog = signal(false);
  first = 0;
  rows = 20;

  readonly jiraConfigMissing = signal(false);

  // Filtering
  readonly globalFilter = computed(() => this.filteringService.globalFilter());
  readonly statusFilter = computed(() => this.filteringService.statusFilter());
  readonly sortedProjects = computed(() => this.filteringService.sortedProjects());
  readonly statusOptions = computed(() => this.filteringService.statusOptions());

  // UI State
  readonly expandedRowKeys = computed(() => this.uiStateService.expandedRowKeys());
  readonly selectedProjectKey = computed(() => this.uiStateService.selectedProjectKey());
  readonly maskByDefault = computed(() => this.uiStateService.maskByDefault());
  readonly selectedProjectsCount = computed(() => this.uiStateService.selectedProjectsCount());

  get selectedProjects(): { key: string }[] {
    return this.uiStateService.selectedProjects();
  }

  set selectedProjects(val: { key: string }[]) {
    this.uiStateService.selectedProjects.set(val);
  }

  // PII Items
  readonly itemsByProject = computed(() => this.piiItemsStorage.itemsByProject());

  // Data
  readonly projects = computed(() => this.dataManagement.projects());
  readonly queue = computed(() => this.dataManagement.queue());
  readonly isProjectsLoading = computed(() => this.dataManagement.isProjectsLoading());
  readonly lastRefresh = computed(() => this.dataManagement.lastRefresh());
  readonly isRefreshing = computed(() => this.dataManagement.isRefreshing());

  // Scan Control
  readonly isStreaming = computed(() => this.scanControl.isStreaming());
  readonly canStartScan = computed(() => this.scanControl.canStartScan());
  readonly canResumeScan = computed(() => this.scanControl.canResumeScan());

  readonly globalSeverityCounts = computed<SeverityCounts>(() => {
    const projects = this.sortedProjects();
    const result = { total: 0, high: 0, medium: 0, low: 0 };
    for (const p of projects) {
      if (p.counts) {
        result.total += p.counts.total;
        result.high += p.counts.high;
        result.medium += p.counts.medium;
        result.low += p.counts.low;
      }
    }
    return result;
  });

  ngOnInit(): void {
    this.jiraConfigService.getConfig().subscribe({
      next: (config) => {
        if (!config.configured) {
          this.jiraConfigMissing.set(true);
          this.dataManagement.isProjectsLoading.set(false);
          return;
        }
        this.dataManagement.fetchProjects().subscribe();
      },
      error: () => {
        this.jiraConfigMissing.set(true);
        this.dataManagement.isProjectsLoading.set(false);
      }
    });

    this.jiraConfigService.configSaved$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.jiraConfigMissing.set(false);
      if (this.projects().length === 0) {
        this.dataManagement.fetchProjects().subscribe();
      }
    });
  }

  ngOnDestroy(): void {
    this.scanControl.reset();
  }

  startAll(): void {
    this.scanControl.startAll();
  }

  startSelected(): void {
    const keys = this.selectedProjects.map(p => p.key);
    this.scanControl.startSelected(keys);
  }

  pauseScan(): void {
    this.scanControl.pauseScan();
  }

  resumeLastScan(): void {
    this.scanControl.resumeLastScan();
  }

  onGlobalChange(value: string): void {
    this.filteringService.onGlobalChange(value);
  }

  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    this.filteringService.onFilter(field, value);
  }

  onCustomSort(event: SortEvent): void {
    this.filteringService.onCustomSort(event);
  }

  onRowExpand(event: { data?: { key?: string } }): void {
    this.uiStateService.onRowExpand(event);
  }

  onRowCollapse(event: { data?: { key?: string } }): void {
    this.uiStateService.onRowCollapse(event);
  }

  refreshProjects(): void {
    this.dataManagement.refreshProjects();
  }

  statusLabel(status?: string): string {
    return this.uiStateService.statusLabel(status);
  }

  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    return this.uiStateService.statusStyle(status);
  }

  statusStyleClass(status?: string): string | undefined {
    return this.uiStateService.statusStyleClass(status);
  }

  openJira(project: { url?: string }): void {
    if (project.url) {
      window.open(project.url, '_blank', 'noopener');
    }
  }

  openPiiHelpDialog(): void {
    this.showPiiHelpDialog.set(true);
  }

  requestOpenSettings(tab: number = 0): void {
    this.openSettings.emit(tab);
  }

  dismissJiraConfigBanner(): void {
    this.jiraConfigMissing.set(false);
  }

  onPageChange(event: { first: number; rows: number }): void {
    this.first = event.first;
    this.rows = event.rows;
  }

  nextPage(): void {
    this.first = this.first + this.rows;
  }

  prevPage(): void {
    this.first = Math.max(0, this.first - this.rows);
  }

  resetPage(): void {
    this.first = 0;
  }

  isLastPage(): boolean {
    const total = this.sortedProjects().length;
    return total === 0 || this.first + this.rows >= total;
  }

  isFirstPage(): boolean {
    return this.first === 0;
  }

  get currentPage(): number {
    return Math.floor(this.first / this.rows) + 1;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.sortedProjects().length / this.rows));
  }
}
