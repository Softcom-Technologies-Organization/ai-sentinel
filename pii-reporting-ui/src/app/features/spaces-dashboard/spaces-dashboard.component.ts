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
import {TranslocoModule} from '@jsverse/transloco';
import {
  LanguageSelectorComponent
} from '../../core/components/language-selector/language-selector.component';
import {PiiItemCardComponent} from '../pii-item-card/pii-item-card.component';
import {PiiSettingsComponent} from '../pii-settings/pii-settings.component';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {BadgeModule} from 'primeng/badge';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {TagModule} from 'primeng/tag';
import {SpacesDashboardUtils} from './spaces-dashboard.utils';
import {Ripple} from 'primeng/ripple';
import {TooltipModule} from 'primeng/tooltip';
import {DataViewModule} from 'primeng/dataview';
import {SkeletonModule} from 'primeng/skeleton';
import {
  ScanProgressBarComponent
} from '../../shared/components/scan-progress-bar/scan-progress-bar.component';
import {SortEvent} from 'primeng/api';
import {TestIds} from '../test-ids.constants';
import {ToastModule} from 'primeng/toast';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {DialogModule} from 'primeng/dialog';
import {
  NewSpacesBannerComponent
} from '../../shared/components/new-spaces-banner/new-spaces-banner.component';
import {SpaceFilteringService} from './services/space-filtering.service';
import {DashboardUiStateService} from './services/dashboard-ui-state.service';
import {PiiItemsStorageService} from './services/pii-items-storage.service';
import {SpaceDataManagementService} from './services/space-data-management.service';
import {ScanControlService} from './services/scan-control.service';

/**
 * Dashboard to orchestrate scanning all Confluence spaces sequentially.
 *
 * Business goal:
 * - Allow starting multi-space scan with user confirmation
 * - Follow real-time scan progress via SSE events
 * - Display detected PII items per space with severity counts
 * - Enable scan pause/resume functionality
 * - Detect and notify about new spaces
 * - Indicate spaces modified since last scan
 *
 * Architecture:
 * - Thin orchestration layer delegating to specialized services
 * - Services handle all business logic and state management
 * - Component focuses on template bindings and user interactions
 *
 * Service Responsibilities:
 * - SpaceFilteringService: Filtering and sorting logic
 * - DashboardUiStateService: UI state (expansion, selection, logs)
 * - PiiItemsStorageService: PII items storage and aggregation
 * - SpaceDataManagementService: Space data loading and polling
 * - ScanControlService: Scan lifecycle (start, stop, resume)
 * - SseEventHandlerService: Real-time event routing and processing
 *
 * @since Phase 7 - Component Refactoring
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    ToggleSwitchModule,
    PiiItemCardComponent,
    PiiSettingsComponent,
    BadgeModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    Ripple,
    TooltipModule,
    DataViewModule,
    SkeletonModule,
    ConfirmDialogModule,
    DialogModule,
    ToastModule,
    TranslocoModule,
    LanguageSelectorComponent,
    NewSpacesBannerComponent,
    ScanProgressBarComponent
  ],
  templateUrl: './spaces-dashboard.component.html',
  styleUrl: './spaces-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpacesDashboardComponent implements OnInit, OnDestroy {
  // Service injections - specialized responsibilities
  private readonly filteringService = inject(SpaceFilteringService);
  private readonly uiStateService = inject(DashboardUiStateService);
  private readonly piiItemsStorage = inject(PiiItemsStorageService);
  private readonly dataManagement = inject(SpaceDataManagementService);
  private readonly scanControl = inject(ScanControlService);

  // Utility services
  readonly spacesDashboardUtils = inject(SpacesDashboardUtils);

  // Expose test IDs to template for E2E testing
  readonly testIds = TestIds.dashboard;

  // 10 placeholder rows for loading skeleton
  readonly skeletonRows: number[] = Array.from({ length: 10 }, (_, i) => i);

  // PII Help dialog visibility
  readonly showPiiHelpDialog = signal(false);

  // Settings dialog visibility
  readonly showSettingsDialog = signal(false);

  // ===== Computed signals exposing service state to template =====

  // Filtering & Sorting
  readonly globalFilter = computed(() => this.filteringService.globalFilter());
  readonly statusFilter = computed(() => this.filteringService.statusFilter());
  readonly sortedSpaces = computed(() => this.filteringService.sortedSpaces());
  readonly statusOptions = computed(() => this.filteringService.statusOptions());

  // UI State
  readonly expandedRowKeys = computed(() => this.uiStateService.expandedRowKeys());
  readonly selectedSpaceKey = computed(() => this.uiStateService.selectedSpaceKey());
  readonly maskByDefault = computed(() => this.uiStateService.maskByDefault());
  readonly lines = computed(() => this.uiStateService.lines());

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

  ngOnInit(): void {
    // Initialize data loading
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

  /**
   * Initiates global scan with user confirmation.
   * Delegates to ScanControlService.
   */
  startAll(): void {
    this.scanControl.startAll();
  }

  /**
   * Pauses current scan and marks as PAUSED for later resume.
   * Delegates to ScanControlService.
   */
  pauseScan(): void {
    this.scanControl.pauseScan();
  }

  /**
   * Resumes paused scan from last checkpoint.
   * Delegates to ScanControlService.
   */
  resumeLastScan(): void {
    this.scanControl.resumeLastScan();
  }

  /**
   * Updates global filter.
   * Delegates to SpaceFilteringService.
   */
  onGlobalChange(value: string): void {
    this.filteringService.onGlobalChange(value);
  }

  /**
   * Updates status filter.
   * Delegates to SpaceFilteringService.
   */
  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    this.filteringService.onFilter(field, value);
  }

  /**
   * Handles custom sort from PrimeNG table.
   * Delegates to SpaceFilteringService.
   */
  onCustomSort(event: SortEvent): void {
    this.filteringService.onCustomSort(event);
  }

  /**
   * Handles row expansion.
   * Delegates to DashboardUiStateService.
   */
  onRowExpand(event: { data?: { key?: string } }): void {
    this.uiStateService.onRowExpand(event);
  }

  /**
   * Handles row collapse.
   * Delegates to DashboardUiStateService.
   */
  onRowCollapse(event: { data?: { key?: string } }): void {
    this.uiStateService.onRowCollapse(event);
  }

  /**
   * Manually refreshes spaces list.
   * Delegates to SpaceDataManagementService.
   */
  refreshSpaces(): void {
    this.dataManagement.refreshSpaces();
  }

  /**
   * Dismisses new spaces notification.
   * Delegates to SpaceDataManagementService.
   */
  dismissNotification(): void {
    this.dataManagement.dismissNotification();
  }

  /**
   * Checks if space has been updated since last scan.
   * Delegates to SpaceDataManagementService.
   */
  hasSpaceBeenUpdated(spaceKey: string): boolean {
    return this.dataManagement.hasSpaceBeenUpdated(spaceKey);
  }

  /**
   * Gets update tooltip for space.
   * Delegates to SpaceDataManagementService.
   */
  getSpaceUpdateTooltip(spaceKey: string): string {
    return this.dataManagement.getSpaceUpdateTooltip(spaceKey);
  }

  // ===== UI Helper methods =====

  /**
   * Gets translated status label.
   * Delegates to DashboardUiStateService.
   */
  statusLabel(status?: string): string {
    return this.uiStateService.statusLabel(status);
  }

  /**
   * Gets status severity for styling.
   * Delegates to DashboardUiStateService.
   */
  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    return this.uiStateService.statusStyle(status);
  }

  /**
   * Opens Confluence space in new tab.
   * Delegates to SpacesDashboardUtils.
   */
  openConfluence(space: any): void {
    this.spacesDashboardUtils.openConfluence(space);
  }

  /**
   * Opens the PII severity help dialog.
   */
  openPiiHelpDialog(): void {
    this.showPiiHelpDialog.set(true);
  }

  /**
   * Opens the settings dialog in modal overlay.
   * Preserves component lifecycle to maintain SSE connection.
   */
  openSettingsDialog(): void {
    this.showSettingsDialog.set(true);
  }

  /**
   * Closes the settings dialog.
   */
  closeSettingsDialog(): void {
    this.showSettingsDialog.set(false);
  }
}
