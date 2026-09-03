import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { ConfirmationService, MessageService } from 'primeng/api';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { Ripple } from 'primeng/ripple';
import {
  FindingTargetStatus,
  ObfuscationJobDto,
  RemediationFindingDto,
  RemediationFindingsSearchRequest,
  RemediationFindingsSearchResponse,
  RemediationGroupBy,
  RemediationGroupDto,
  RemediationScope,
  RemediationStatusFilter
} from '../../core/models/remediation.model';
import { RemediationApiService } from '../../core/services/remediation-api.service';
import { RemediationConfigService } from '../../core/services/remediation-config.service';
import { AppHeaderComponent } from '../app-header/app-header.component';
import { ObfuscationSelectionService } from './services/obfuscation-selection.service';
import { ObfuscationViewStateService } from './services/obfuscation-view-state.service';
import { BulkChip, ObfuscationBulkBarComponent } from './components/obfuscation-bulk-bar/obfuscation-bulk-bar.component';
import { ObfuscationConfirmDialogComponent } from './components/obfuscation-confirm-dialog/obfuscation-confirm-dialog.component';
import { ObfuscationFindingRowComponent } from './components/obfuscation-finding-row/obfuscation-finding-row.component';
import { ObfuscationJobProgressComponent } from './components/obfuscation-job-progress/obfuscation-job-progress.component';
import { TestIds } from '../test-ids.constants';

const ALL_SEVERITIES = ['HIGH', 'MEDIUM', 'LOW'];
const PAGE_SIZES = [20, 50, 100];

interface StatusFilterOption {
  value: RemediationStatusFilter;
  labelKey: string;
}

const STATUS_FILTER_OPTIONS: StatusFilterOption[] = [
  { value: 'ALL', labelKey: 'obfuscation.filter.all' },
  { value: 'PENDING', labelKey: 'obfuscation.filter.pending' },
  { value: 'HANDLED', labelKey: 'obfuscation.filter.treated' },
  { value: 'FALSE_POSITIVE', labelKey: 'obfuscation.filter.fp' }
];

interface GroupByOption {
  value: RemediationGroupBy;
  labelKey: string;
}

const GROUP_BY_OPTIONS: GroupByOption[] = [
  { value: 'type', labelKey: 'obfuscation.groupBy.type' },
  { value: 'severity', labelKey: 'obfuscation.groupBy.severity' }
];

type BulkStatus = Exclude<FindingTargetStatus, 'PENDING'>;

interface BulkStatusAction {
  confirmKey: string;
  icon: string;
  toastKey: string;
}

const BULK_STATUS_ACTIONS: Record<BulkStatus, BulkStatusAction> = {
  MANUALLY_HANDLED: {
    confirmKey: 'confirmations.markTreated',
    icon: 'pi pi-check',
    toastKey: 'obfuscation.toast.treated'
  },
  FALSE_POSITIVE: {
    confirmKey: 'confirmations.reportFalsePositive',
    icon: 'pi pi-flag',
    toastKey: 'obfuscation.toast.fpReported'
  }
};

function stripBoldTags(value: string): string {
  return value.replace(/<\/?b>/g, '');
}

/**
 * Container for the PII obfuscation view. Orchestrates the backend calls
 * (search, plan, status transitions, job) around pure UI state; every
 * displayed aggregate comes verbatim from the backend responses.
 */
@Component({
  selector: 'app-pii-obfuscation',
  standalone: true,
  imports: [
    TranslocoModule,
    FormsModule,
    TableModule,
    ButtonModule,
    CheckboxModule,
    ConfirmDialogModule,
    Ripple,
    AppHeaderComponent,
    ObfuscationFindingRowComponent,
    ObfuscationBulkBarComponent,
    ObfuscationConfirmDialogComponent,
    ObfuscationJobProgressComponent
  ],
  providers: [ObfuscationSelectionService, ObfuscationViewStateService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pii-obfuscation.component.html',
  styleUrl: './pii-obfuscation.component.css',
})
export class PiiObfuscationComponent {
  readonly remediationConfig = inject(RemediationConfigService);
  readonly selection = inject(ObfuscationSelectionService);
  readonly viewState = inject(ObfuscationViewStateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly remediationApi = inject(RemediationApiService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly transloco = inject(TranslocoService);

  readonly testIds = TestIds.obfuscation;
  readonly statusFilterOptions = STATUS_FILTER_OPTIONS;
  readonly groupByOptions = GROUP_BY_OPTIONS;
  readonly pageSizes = PAGE_SIZES;

  readonly preselectRequested = signal(false);
  readonly bannerDismissed = signal(false);
  readonly dialogVisible = signal(false);
  readonly activeJobId = signal<string | null>(null);

  readonly totals = computed(() => this.viewState.lastSearchResponse()?.totals ?? null);
  readonly groups = computed(() => this.viewState.lastSearchResponse()?.groups ?? []);
  readonly showBanner = computed(() => this.preselectRequested() && !this.bannerDismissed());

  // "Select all pending" checks every severity with no per-finding exclusion.
  // Detecting that state lets the single button toggle to "deselect all".
  readonly allPendingSelected = computed(() => {
    const checkedSeverities = this.selection.checkedSeverities();
    return (
      ALL_SEVERITIES.every((severity) => checkedSeverities.has(severity)) &&
      this.selection.excludedFindingIds().size === 0
    );
  });

  readonly itemChipLabel = computed(() => {
    const scope = this.selection.scope();
    return scope?.attachmentName ?? scope?.pageId ?? null;
  });

  readonly selectedChips = computed<BulkChip[]>(() => {
    const axis = this.viewState.groupBy();
    return this.groups()
      .filter((group) => group.selectedCount > 0)
      .map((group) => ({
        key: group.key,
        label: group.label,
        severity: group.severity ?? (axis === 'severity' ? group.key : ''),
        selectedCount: group.selectedCount
      }));
  });

  // A bulk status change also reaches attachment findings, which redaction skips and the
  // plan reports apart; the announced count must add them back to stay honest.
  readonly selectionAffectedCount = computed(() => {
    const plan = this.viewState.lastPlan();
    return (plan?.totalFindings ?? 0) + (plan?.attachmentExclusions ?? 0);
  });

  // Pagination is by group (a type/severity group is never split across pages),
  // so the pager navigates groups: its bounds use totalGroups, not the finding count.
  readonly pagerFirst = computed(() => {
    const response = this.viewState.lastSearchResponse();
    if (!response || response.totalGroups === 0) {
      return 0;
    }
    return response.page * response.pageSize + 1;
  });

  readonly pagerLast = computed(() => {
    const response = this.viewState.lastSearchResponse();
    if (!response) {
      return 0;
    }
    return Math.min((response.page + 1) * response.pageSize, response.totalGroups);
  });

  readonly hasPrev = computed(() => (this.viewState.lastSearchResponse()?.page ?? 0) > 0);

  readonly hasNext = computed(() => {
    const response = this.viewState.lastSearchResponse();
    if (!response) {
      return false;
    }
    return (response.page + 1) * response.pageSize < response.totalGroups;
  });

  // Total number of groups across all pages — the paginator counts groups, not findings.
  readonly totalGroups = computed(() => this.viewState.lastSearchResponse()?.totalGroups ?? 0);

  // PrimeNG paginator offset (rows before the current page), in group units.
  readonly pagerOffset = computed(() => this.viewState.page() * this.viewState.pageSize());

  readonly currentPage = computed(() => this.viewState.page() + 1);

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalGroups() / this.viewState.pageSize()))
  );

  // p-table expects a { key: true } map; derive it from the open-accordion set.
  readonly expandedRowKeys = computed<Record<string, boolean>>(() => {
    const map: Record<string, boolean> = {};
    for (const key of this.viewState.openAccordions()) {
      map[key] = true;
    }
    return map;
  });

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => this.enterFromParams(params));
  }

  goToSettings(): void {
    this.router.navigate(['/settings']);
  }

  statusCount(filter: RemediationStatusFilter): number | null {
    const totals = this.totals();
    if (!totals) {
      return null;
    }
    const byFilter: Record<RemediationStatusFilter, number> = {
      ALL: totals.total,
      PENDING: totals.pending,
      HANDLED: totals.handled,
      FALSE_POSITIVE: totals.falsePositive
    };
    return byFilter[filter];
  }

  percentOfTotal(part: number): number {
    const total = this.totals()?.total ?? 0;
    return total > 0 ? (part / total) * 100 : 0;
  }

  onSearchInput(event: Event): void {
    this.viewState.searchText.set((event.target as HTMLInputElement).value);
    this.viewState.page.set(0);
    this.refreshSearch();
  }

  setStatusFilter(filter: RemediationStatusFilter): void {
    this.viewState.statusFilter.set(filter);
    this.viewState.page.set(0);
    this.refreshSearch();
  }

  setGroupBy(axis: RemediationGroupBy): void {
    if (this.viewState.groupBy() === axis) {
      return;
    }
    this.viewState.groupBy.set(axis);
    this.viewState.page.set(0);
    // Open accordions are keyed by the previous axis, so they no longer match any group.
    this.viewState.collapseAll();
    this.refreshSearch();
  }

  expandAll(): void {
    this.viewState.openAll(this.groups().map((group) => group.key));
  }

  collapseAll(): void {
    this.viewState.collapseAll();
  }

  prevPage(): void {
    if (this.hasPrev()) {
      this.viewState.page.update((page) => page - 1);
      this.refreshSearch();
    }
  }

  nextPage(): void {
    if (this.hasNext()) {
      this.viewState.page.update((page) => page + 1);
      this.refreshSearch();
    }
  }

  resetPage(): void {
    if (this.viewState.page() === 0) {
      return;
    }
    this.viewState.page.set(0);
    this.refreshSearch();
  }

  // Bottom PrimeNG paginator gesture. Guarded so PrimeNG's binding-driven emits
  // (init / programmatic [first] changes) never trigger a redundant backend search.
  onTablePage(event: { first: number; rows: number }): void {
    const nextPage = Math.floor(event.first / event.rows);
    const nextSize = event.rows;
    if (nextPage === this.viewState.page() && nextSize === this.viewState.pageSize()) {
      return;
    }
    this.viewState.pageSize.set(nextSize);
    this.viewState.page.set(nextPage);
    this.refreshSearch();
  }

  onRowExpand(event: { data?: { key?: string } }): void {
    const key = event.data?.key;
    if (key) {
      this.viewState.openAccordions.update((open) => new Set(open).add(key));
    }
  }

  onRowCollapse(event: { data?: { key?: string } }): void {
    const key = event.data?.key;
    if (key) {
      this.viewState.openAccordions.update((open) => {
        const next = new Set(open);
        next.delete(key);
        return next;
      });
    }
  }

  severityOf(group: RemediationGroupDto): string {
    const severity = this.viewState.groupBy() === 'severity' ? group.key : group.severity;
    return (severity ?? '').toLowerCase();
  }

  removeItemFilter(): void {
    const scope = this.selection.scope();
    if (!scope) {
      return;
    }
    this.selection.setScope({ spaceKey: scope.spaceKey });
    this.viewState.page.set(0);
    this.refreshAll();
  }

  toggleSelectAllPending(): void {
    if (this.allPendingSelected()) {
      this.clearSelection();
    } else {
      this.selectAllPending();
    }
  }

  selectAllPending(): void {
    this.applySelectAllPending();
    this.refreshAll();
  }

  onMasterToggled(group: RemediationGroupDto): void {
    if (this.viewState.groupBy() === 'type') {
      this.toggleTypeCriterion(group);
    } else {
      this.toggleSeverityCriterion(group);
    }
    this.refreshAll();
  }

  onRowToggled(finding: RemediationFindingDto): void {
    if (finding.status === 'FALSE_POSITIVE') {
      this.selection.includeFinding(finding.findingId);
      this.changeStatus(finding.findingId, 'PENDING');
      return;
    }
    if (finding.selected) {
      this.selection.excludeFinding(finding.findingId);
      this.changeStatus(finding.findingId, 'FALSE_POSITIVE');
      return;
    }
    this.selection.includeFinding(finding.findingId);
    this.refreshAll();
  }

  onRowMarkManual(finding: RemediationFindingDto): void {
    this.changeStatus(finding.findingId, 'MANUALLY_HANDLED');
  }

  onRowRestore(finding: RemediationFindingDto): void {
    this.selection.forgetFinding(finding.findingId);
    this.changeStatus(finding.findingId, 'PENDING');
  }

  clearSelection(): void {
    this.selection.clear();
    this.refreshAll();
  }

  markSelectionTreated(): void {
    this.confirmSelectionStatus('MANUALLY_HANDLED');
  }

  reportSelectionFalsePositive(): void {
    this.confirmSelectionStatus('FALSE_POSITIVE');
  }

  openConfirmDialog(): void {
    this.remediationApi.planObfuscation(this.selection.buildSelectionDto()).subscribe((plan) => {
      this.viewState.lastPlan.set(plan);
      this.dialogVisible.set(true);
    });
  }

  confirmObfuscation(): void {
    const plan = this.viewState.lastPlan();
    if (!plan) {
      return;
    }
    this.remediationApi
      .createJob({
        selection: this.selection.buildSelectionDto(),
        selectionChecksum: plan.selectionChecksum
      })
      .subscribe({
        next: (created) => this.startJob(created.jobId),
        error: (error) => this.onJobCreationError(error)
      });
  }

  onJobCompleted(job: ObfuscationJobDto): void {
    this.activeJobId.set(null);
    this.notifyJobResult(job);
    this.selection.clear();
    this.refreshAll();
  }

  private enterFromParams(params: ParamMap): void {
    this.selection.setScope(toScope(params));
    const preselect = params.get('preselect') === 'true';
    this.preselectRequested.set(preselect);
    if (preselect) {
      this.applySelectAllPending();
    }
    this.refreshAll();
  }

  private applySelectAllPending(): void {
    ALL_SEVERITIES.forEach((severity) => this.selection.checkSeverity(severity));
  }

  private refreshAll(): void {
    this.refreshSearch();
    this.refreshPlan();
  }

  private refreshSearch(): void {
    if (!this.remediationConfig.enabled()) {
      return;
    }
    this.viewState.loading.set(true);
    this.remediationApi.searchFindings(this.buildSearchRequest()).subscribe({
      next: (response) => this.applySearchResponse(response),
      error: () => this.viewState.loading.set(false)
    });
  }

  private buildSearchRequest(): RemediationFindingsSearchRequest {
    const request: RemediationFindingsSearchRequest = {
      scope: this.selection.scope() ?? { spaceKey: '' },
      groupBy: this.viewState.groupBy(),
      statusFilter: this.viewState.statusFilter(),
      page: this.viewState.page(),
      pageSize: this.viewState.pageSize(),
      selection: this.selection.buildSelectionDto()
    };
    const searchText = this.viewState.searchText();
    if (searchText) {
      request.searchText = searchText;
    }
    const itemFilter = this.viewState.itemFilter();
    if (itemFilter) {
      request.itemFilter = itemFilter;
    }
    return request;
  }

  private applySearchResponse(response: RemediationFindingsSearchResponse): void {
    this.viewState.lastSearchResponse.set(response);
    this.viewState.loading.set(false);
  }

  private refreshPlan(): void {
    if (!this.remediationConfig.enabled() || !this.hasSelectionCriteria()) {
      this.viewState.lastPlan.set(null);
      return;
    }
    this.remediationApi
      .planObfuscation(this.selection.buildSelectionDto())
      .subscribe((plan) => this.viewState.lastPlan.set(plan));
  }

  private hasSelectionCriteria(): boolean {
    return (
      this.selection.checkedTypes().size > 0 ||
      this.selection.checkedSeverities().size > 0 ||
      this.selection.includedFindingIds().size > 0
    );
  }

  private changeStatus(findingId: string, targetStatus: FindingTargetStatus): void {
    this.remediationApi
      .changeFindingsStatus({ changes: [{ findingId, targetStatus }] })
      .subscribe(() => this.refreshAll());
  }

  private confirmSelectionStatus(targetStatus: BulkStatus): void {
    if (!this.hasSelectionCriteria()) {
      return;
    }
    const action = BULK_STATUS_ACTIONS[targetStatus];
    const count = this.selectionAffectedCount();
    this.confirmationService.confirm({
      header: this.transloco.translate(`${action.confirmKey}.header`),
      message: this.transloco.translate(`${action.confirmKey}.message`, { count }),
      icon: action.icon,
      acceptLabel: this.transloco.translate(`${action.confirmKey}.acceptLabel`, { count }),
      rejectLabel: this.transloco.translate(`${action.confirmKey}.rejectLabel`),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary',
      accept: () => this.changeSelectionStatus(targetStatus, action.toastKey)
    });
  }

  private changeSelectionStatus(targetStatus: FindingTargetStatus, toastKey: string): void {
    if (!this.hasSelectionCriteria()) {
      return;
    }
    this.remediationApi
      .changeFindingsStatusBySelection({
        selection: this.selection.buildSelectionDto(),
        targetStatus
      })
      .subscribe((result) => {
        this.notify('success', toastKey, { count: result.applied.length });
        this.selection.clear();
        this.refreshAll();
      });
  }

  private startJob(jobId: string): void {
    this.dialogVisible.set(false);
    this.activeJobId.set(jobId);
  }

  private toggleTypeCriterion(group: RemediationGroupDto): void {
    if (group.masterState !== 'all') {
      this.selection.checkType(group.key);
    } else if (this.selection.checkedTypes().has(group.key)) {
      this.selection.uncheckType(group.key);
    } else {
      this.deselectGroupCrossAxis('type', group.key);
    }
  }

  private toggleSeverityCriterion(group: RemediationGroupDto): void {
    if (group.masterState !== 'all') {
      this.selection.checkSeverity(group.key);
    } else if (this.selection.checkedSeverities().has(group.key)) {
      this.selection.uncheckSeverity(group.key);
    } else {
      this.deselectGroupCrossAxis('severity', group.key);
    }
  }

  /**
   * A group can be fully selected through the other axis (e.g. every type group is
   * "all" because all severities are checked). Unchecking such a group is materialised
   * by pinning the current-axis criteria to the still-selected groups, minus this one,
   * so the deselection survives the backend re-resolution.
   */
  private deselectGroupCrossAxis(axis: RemediationGroupBy, key: string): void {
    const remainingKeys = this.groups()
      .filter((group) => group.masterState !== 'none' && group.key !== key)
      .map((group) => group.key);
    if (axis === 'type') {
      this.selection.setCheckedTypes(remainingKeys);
      this.selection.setCheckedSeverities([]);
    } else {
      this.selection.setCheckedSeverities(remainingKeys);
      this.selection.setCheckedTypes([]);
    }
  }

  private onJobCreationError(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.remediationApi
        .planObfuscation(this.selection.buildSelectionDto())
        .subscribe((plan) => this.viewState.lastPlan.set(plan));
    }
  }

  private notifyJobResult(job: ObfuscationJobDto): void {
    if (job.status === 'COMPLETED') {
      this.notify('success', 'obfuscation.toast.obfuscated', { count: job.processed });
    } else {
      this.notify('warn', 'obfuscation.toast.completedWithIssues');
    }
  }

  private notify(
    severity: 'success' | 'warn',
    messageKey: string,
    params?: Record<string, unknown>
  ): void {
    const summaryKey = severity === 'warn' ? 'common.warning' : 'common.success';
    this.messageService.add({
      severity,
      summary: this.transloco.translate(summaryKey),
      detail: stripBoldTags(this.transloco.translate(messageKey, params))
    });
  }
}

function toScope(params: ParamMap): RemediationScope {
  const scope: RemediationScope = { spaceKey: params.get('spaceKey') ?? '' };
  const pageId = params.get('pageId');
  if (pageId) {
    scope.pageId = pageId;
  }
  const attachmentName = params.get('attachmentName');
  if (attachmentName) {
    scope.attachmentName = attachmentName;
  }
  return scope;
}
