import { ChangeDetectionStrategy, Component, computed, EventEmitter, inject, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TranslocoModule } from '@jsverse/transloco';
import { PiiPageCardComponent } from '../pii-page-card/pii-page-card.component';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { BadgeModule } from 'primeng/badge';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { SpacesDashboardUtils } from './spaces-dashboard.utils';
import { Ripple } from 'primeng/ripple';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { ScanProgressBarComponent } from '../../shared/components/scan-progress-bar/scan-progress-bar.component';
import { SortEvent } from 'primeng/api';
import { TestIds } from '../test-ids.constants';
import { DialogModule } from 'primeng/dialog';
import { NewSpacesBannerComponent } from '../../shared/components/new-spaces-banner/new-spaces-banner.component';
import { ConfluenceConfigBannerComponent } from '../../shared/components/confluence-config-banner/confluence-config-banner.component';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import { SpaceFilteringService } from './services/space-filtering.service';
import { DashboardUiStateService } from './services/dashboard-ui-state.service';
import { PiiItemsStorageService } from './services/pii-items-storage.service';
import { SpaceDataManagementService } from './services/space-data-management.service';
import { ScanControlService } from './services/scan-control.service';
import { SeverityCardsComponent } from '../severity-cards/severity-cards.component';
import { SeverityCounts } from '../../core/models/severity-counts';

/**
 * Confluence source dashboard - displays spaces table with PII scan results.
 *
 * Embedded inside AppShellComponent's Confluence tab panel.
 * Delegates to specialized services for all business logic.
 */
@Component({
  selector: 'app-confluence-dashboard',
  standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ButtonModule,
        ToggleSwitchModule,
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
        DialogModule,
        TranslocoModule,
        NewSpacesBannerComponent,
        ConfluenceConfigBannerComponent,
        ScanProgressBarComponent,
        SeverityCardsComponent
    ],
  templateUrl: './confluence-dashboard.component.html',
  styleUrl: './confluence-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfluenceDashboardComponent implements OnInit, OnDestroy {
  // Service injections - specialized responsibilities
  private readonly filteringService = inject(SpaceFilteringService);
  private readonly uiStateService = inject(DashboardUiStateService);
  private readonly piiItemsStorage = inject(PiiItemsStorageService);
  private readonly dataManagement = inject(SpaceDataManagementService);
  private readonly scanControl = inject(ScanControlService);
  private readonly confluenceConfigService = inject(ConfluenceConnectionConfigService);

  // Utility services
  readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  // Output to request settings dialog from parent (AppShellComponent)
  @Output() openSettings = new EventEmitter<number>();

  // Expose test IDs to template for E2E testing
  readonly testIds = TestIds.dashboard;

  // 10 placeholder rows for loading skeleton
  readonly skeletonRows: number[] = Array.from({ length: 10 }, (_, i) => i);

  // PII Help dialog visibility
  readonly showPiiHelpDialog = signal(false);

  // Paginator state
  first = 0;
  rows = 20;

  // Confluence config missing warning
  readonly confluenceConfigMissing = signal(false);

  // ===== Computed signals exposing service state to template =====

  // Filtering & Sorting
  readonly globalFilter = computed(() => this.filteringService.globalFilter());
  readonly statusFilter = computed(() => this.filteringService.statusFilter());
  readonly modifiedOnlyFilter = computed(() => this.filteringService.modifiedOnlyFilter());
  readonly sortedSpaces = computed(() => this.filteringService.sortedSpaces());
  readonly statusOptions = computed(() => this.filteringService.statusOptions());

  // UI State
  readonly expandedRowKeys = computed(() => this.uiStateService.expandedRowKeys());
  readonly selectedSpaceKey = computed(() => this.uiStateService.selectedSpaceKey());
  readonly maskByDefault = computed(() => this.uiStateService.maskByDefault());
  readonly lines = computed(() => this.uiStateService.lines());
  readonly selectedSpacesCount = computed(() => this.uiStateService.selectedSpacesCount());

  get selectedSpaces(): any[] {
    return this.uiStateService.selectedSpaces();
  }

  set selectedSpaces(val: any[]) {
    this.uiStateService.selectedSpaces.set(val);
  }

  // PII Items
  readonly itemsBySpace = computed(() => this.piiItemsStorage.itemsBySpace());

  // Space Data
  readonly spaces = computed(() => this.dataManagement.spaces());
  readonly queue = computed(() => this.dataManagement.queue());
  readonly isSpacesLoading = computed(() => this.dataManagement.isSpacesLoading());
  readonly lastScanMeta = computed(() => this.dataManagement.lastScanMeta());
  readonly lastRefresh = computed(() => this.dataManagement.lastRefresh());
  readonly isRefreshing = computed(() => this.dataManagement.isRefreshing());
  readonly hasNewSpaces = computed(() => this.dataManagement.hasNewSpaces());
  readonly newSpacesCount = computed(() => this.dataManagement.newSpacesCount());

  // Scan Control
  readonly isStreaming = computed(() => this.scanControl.isStreaming());
  readonly isResuming = computed(() => this.scanControl.isResuming());
  readonly activeSpaceKey = computed(() => this.uiStateService.activeSpaceKey());
  readonly canStartScan = computed(() => this.scanControl.canStartScan());
  readonly canResumeScan = computed(() => this.scanControl.canResumeScan());

  // Global severity counts across all displayed spaces
  readonly globalSeverityCounts = computed<SeverityCounts>(() => {
    const spaces = this.sortedSpaces();
    const result = { total: 0, high: 0, medium: 0, low: 0 };
    for (const s of spaces) {
      if (s.counts) {
        result.total += s.counts.total;
        result.high += s.counts.high;
        result.medium += s.counts.medium;
        result.low += s.counts.low;
      }
    }
    return result;
  });

  ngOnInit(): void {
    // Check if Confluence connection is configured before loading data
    this.confluenceConfigService.getConfig().subscribe({
      next: (config) => {
        if (!config.configured) {
          this.confluenceConfigMissing.set(true);
          this.dataManagement.isSpacesLoading.set(false);
          return;
        }
        this.initializeDataLoading();
      },
      error: () => {
        this.confluenceConfigMissing.set(true);
        this.dataManagement.isSpacesLoading.set(false);
      }
    });
  }

  private initializeDataLoading(): void {
    this.dataManagement.fetchSpaces().subscribe();
    this.dataManagement.loadLastScan().subscribe();
    this.dataManagement.loadSpacesUpdateInfo().subscribe();

    // Load last scan summary (spaces statuses and PII items) for page refresh
    // isActive=false initially, but may detect RUNNING scan and reconnect
    this.dataManagement.loadLastSpaceStatuses(false, true).subscribe({
      next: () => {
        // Phase 2: Auto-reconnect to running scan after page refresh
        // If a scan was running when browser was closed/refreshed, reconnect SSE stream
        setTimeout(() => {
          this.scanControl.checkAndReconnectToRunningScan();
        }, 100);
      }
    });
  }

  ngOnDestroy(): void {
    this.scanControl.reset();
    this.dataManagement.stopBackgroundPolling();
  }

  // ===== Template event handlers - delegation to services =====

  startAll(): void {
    this.scanControl.startAll();
  }

  startSelected(): void {
    const keys = this.selectedSpaces.map(s => s.key);
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

  onModifiedOnlyChange(value: boolean): void {
    this.filteringService.onModifiedOnlyChange(value);
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

  refreshSpaces(): void {
    this.dataManagement.refreshSpaces();
  }

  dismissNotification(): void {
    this.dataManagement.dismissNotification();
  }

  hasSpaceBeenUpdated(spaceKey: string): boolean {
    return this.dataManagement.hasSpaceBeenUpdated(spaceKey);
  }

  getSpaceUpdateTooltip(spaceKey: string): string {
    return this.dataManagement.getSpaceUpdateTooltip(spaceKey);
  }

  // ===== UI Helper methods =====

  statusLabel(status?: string): string {
    return this.uiStateService.statusLabel(status);
  }

  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    return this.uiStateService.statusStyle(status);
  }

  openConfluence(space: any): void {
    this.spacesDashboardUtils.openConfluence(space);
  }

  openPiiHelpDialog(): void {
    this.showPiiHelpDialog.set(true);
  }

  requestOpenSettings(tab: number = 0): void {
    this.openSettings.emit(tab);
  }

  dismissConfluenceConfigBanner(): void {
    this.confluenceConfigMissing.set(false);
  }

  // ===== Paginator navigation =====

  onPageChange(event: { first: number; rows: number }): void {
    this.first = event.first;
    this.rows = event.rows;
  }

  nextPage(): void {
    this.first = this.first + this.rows;
  }

  prevPage(): void {
    this.first = this.first - this.rows;
  }

  resetPage(): void {
    this.first = 0;
  }

  isLastPage(): boolean {
    const total = this.sortedSpaces().length;
    return total === 0 || this.first + this.rows >= total;
  }

  isFirstPage(): boolean {
    return this.first === 0;
  }

  get currentPage(): number {
    return Math.floor(this.first / this.rows) + 1;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.sortedSpaces().length / this.rows));
  }
}
