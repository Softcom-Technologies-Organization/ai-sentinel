import { computed, inject, Injectable, signal, Signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { SortEvent } from 'primeng/api';
import { SpacesDashboardUtils, UISpace } from '../spaces-dashboard.utils';
import { SpaceDataManagementService } from './space-data-management.service';
import { PiiDetectionConfigService } from '../../../core/services/pii-detection-config.service';
import { PiiTypeConfig } from '../../../core/models/pii-detection-config.model';
import {
  DashboardFacets,
  DashboardFilterParams,
  FacetCount,
  SentinelleApiService
} from '../../../core/services/sentinelle-api.service';

// Re-exported so existing consumers (e.g. the filter bar) keep importing it from here.
export type { FacetCount } from '../../../core/services/sentinelle-api.service';

/** Severity filter values. */
export type SeverityFilterValue = 'HIGH' | 'MEDIUM' | 'LOW';

/** A selectable PII type option grouped under its category. */
export interface PiiTypeOption {
  /** PII type code (e.g. "EMAIL"). */
  code: string;
  /** i18n key for the type label. */
  labelKey: string;
  /** Detector name shown as a subtitle (e.g. "PRESIDIO"). */
  detector: string;
}

/** A category group of PII type options for the grouped multi-select. */
export interface PiiTypeGroup {
  /** Category code (used to build the i18n label). */
  category: string;
  /** i18n key for the category label. */
  labelKey: string;
  /** PII type options belonging to this category. */
  items: PiiTypeOption[];
}

/** Empty facet set used before the first server response. */
const EMPTY_FACETS: DashboardFacets = { piiTypes: {}, severities: {}, statuses: {} };

/**
 * Holds the dashboard filter / sort / search state and drives SERVER-SIDE
 * filtering: every state change triggers a debounced fetch of the dashboard
 * endpoint with the criteria as query parameters.
 *
 * The server is authoritative for which spaces match and in what order, plus
 * the contextual facet counts. The rows themselves are rendered from the live
 * client store (SpacesDashboardUtils), ordered/filtered by the server result,
 * so live scan updates keep flowing while filtering/sorting stays server-side.
 *
 * SOLID:
 * - Single Responsibility: owns the filter state + server query orchestration.
 * - Dependency Inversion: depends on the API/util/config abstractions.
 */
@Injectable({
  providedIn: 'root'
})
export class SpaceFilteringService {
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  private readonly dataManagement = inject(SpaceDataManagementService);
  private readonly piiConfigService = inject(PiiDetectionConfigService);
  private readonly apiService = inject(SentinelleApiService);

  // ===== Filter state =====
  readonly globalFilter = signal<string>('');
  readonly piiTypeFilter = signal<string[]>([]);
  readonly severityFilter = signal<string[]>([]);
  readonly statusFilter = signal<string[]>([]);
  readonly modifiedOnlyFilter = signal<boolean>(false);

  // ===== Sort state =====
  /** 'name' | 'totalDetections' | 'severityScore' | 'lastScan' | 'piiType:<CODE>' | null. */
  readonly sortCriterion = signal<string | null>(null);
  readonly sortOrder = signal<number>(1); // 1 ascending, -1 descending
  /** Legacy field kept for PrimeNG header sort compatibility. */
  readonly sortField = signal<string | null>(null);

  /**
   * Column field the table must be bound to, so that it never holds a sort of
   * its own: PrimeNG re-emits (onSort) with its own sortField/sortOrder on every
   * [value] change, which would otherwise overwrite the criterion chosen here.
   */
  readonly tableSortField = computed<string | null>(() => {
    const criterion = this.sortCriterion();
    return criterion === 'severityScore' ? 'piiCount' : criterion;
  });

  // ===== Server-driven results =====
  private readonly orderedKeys = signal<string[]>([]);
  private readonly facets = signal<DashboardFacets>(EMPTY_FACETS);
  private readonly serverTotalCount = signal<number>(0);
  readonly loading = signal<boolean>(false);

  // ===== PII type universe (option list), loaded from backend config =====
  private readonly piiTypeConfigs: Signal<PiiTypeConfig[]> = toSignal(
    this.piiConfigService.getAllPiiTypeConfigs().pipe(catchError(() => of([] as PiiTypeConfig[]))),
    { initialValue: [] as PiiTypeConfig[] }
  );

  /** PII type options grouped by category for the multi-select. */
  readonly piiTypeGroups = computed<PiiTypeGroup[]>(() => {
    const byCategory = new Map<string, Map<string, PiiTypeOption>>();
    for (const cfg of this.piiTypeConfigs()) {
      const category = cfg.category || 'CUSTOM';
      const group = byCategory.get(category) ?? new Map<string, PiiTypeOption>();
      if (!group.has(cfg.piiType)) {
        group.set(cfg.piiType, {
          code: cfg.piiType,
          labelKey: `settings.piiTypes.typeNames.${cfg.piiType}`,
          detector: cfg.detector
        });
      }
      byCategory.set(category, group);
    }
    return Array.from(byCategory.entries()).map(([category, items]) => ({
      category,
      labelKey: `settings.piiTypes.categoryNames.${category}`,
      items: Array.from(items.values())
    }));
  });

  /** Available status options for filtering (delegates to utils). */
  readonly statusOptions = computed(() => this.spacesDashboardUtils.statusOptions());

  /** Severity filter options (Critique / Modéré / Faible). */
  readonly severityOptions: ReadonlyArray<{ labelKey: string; value: SeverityFilterValue }> = [
    { labelKey: 'dashboard.filters.severityCritical', value: 'HIGH' },
    { labelKey: 'dashboard.filters.severityModerate', value: 'MEDIUM' },
    { labelKey: 'dashboard.filters.severityLow', value: 'LOW' }
  ];

  /** Criteria sent to the backend; recomputed whenever any filter/sort signal changes. */
  private readonly criteria = computed<DashboardFilterParams>(() => ({
    piiTypes: this.piiTypeFilter(),
    severities: this.severityFilter(),
    statuses: this.statusFilter(),
    q: (this.globalFilter() ?? '').trim() || undefined,
    sort: this.sortCriterion() ?? undefined,
    order: this.sortOrder() === 1 ? 'asc' : 'desc'
  }));

  private readonly reloadCounter = signal(0);

  /**
   * Fetch trigger: the counter is part of the deduplication key but never reaches the query
   * string, so reload() can force a fetch with criteria the server has already been asked for.
   */
  private readonly fetchTrigger = computed(() => ({
    reloadCounter: this.reloadCounter(),
    params: this.criteria()
  }));

  /**
   * Re-runs the server query with the current criteria.
   *
   * A failed fetch leaves orderedKeys empty, and distinctUntilChanged suppresses any retry as
   * long as no criterion changes — so recovering from a backend outage needs an explicit nudge.
   */
  reload(): void {
    this.reloadCounter.update(value => value + 1);
  }

  constructor() {
    // Debounced server fetch: the criteria drive filtering/sorting/search server-side.
    toObservable(this.fetchTrigger)
      .pipe(
        debounceTime(200),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        switchMap(({ params }) => {
          this.loading.set(true);
          return this.apiService.getDashboardSpacesSummary(params).pipe(catchError(() => of(null)));
        })
      )
      .subscribe(response => {
        this.loading.set(false);
        if (!response) {
          return;
        }
        this.orderedKeys.set(response.spaces.map(s => s.spaceKey));
        this.facets.set(response.facets ?? EMPTY_FACETS);
        this.serverTotalCount.set(response.spacesCount ?? response.spaces.length);
      });
  }

  // ===== Table source: live store rows, ordered/filtered by the server =====

  /** Final list bound to the table: store spaces in the server-decided order. */
  readonly sortedSpaces = computed<UISpace[]>(() => {
    const order = this.orderedKeys();
    const byKey = new Map(this.spacesDashboardUtils.allSpaces().map(s => [s.key, s] as const));
    let result = order
      .map(key => byKey.get(key))
      .filter((s): s is UISpace => s != null);

    if (this.modifiedOnlyFilter()) {
      const updateInfos = this.dataManagement.spacesUpdateInfo();
      result = result.filter(s =>
        updateInfos.find(i => i.spaceKey === s.key)?.hasBeenUpdated ?? false
      );
    }
    return result;
  });

  /** Total number of spaces available (server-reported, before filtering). */
  readonly totalSpacesCount = computed<number>(() => this.serverTotalCount());

  // ===== Facets (server-computed, contextual) =====
  readonly piiTypeFacetCounts = computed<Record<string, FacetCount>>(() => this.facets().piiTypes ?? {});
  readonly severityFacetCounts = computed<Record<string, FacetCount>>(() => this.facets().severities ?? {});
  readonly statusFacetCounts = computed<Record<string, FacetCount>>(() => this.facets().statuses ?? {});

  /** True when any filter, search or sort is active. */
  readonly isResettable = computed<boolean>(() =>
    (this.globalFilter() ?? '').trim().length > 0
    || this.piiTypeFilter().length > 0
    || this.severityFilter().length > 0
    || this.statusFilter().length > 0
    || this.sortCriterion() != null
    || this.modifiedOnlyFilter()
  );

  // ===== Public API =====

  /** Updates the global search filter and keeps SpacesDashboardUtils in sync. */
  onGlobalChange(value: string): void {
    this.globalFilter.set(value);
    this.spacesDashboardUtils.globalFilter.set(value);
  }

  /** Updates a single legacy filter field (status migrates to multi). */
  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    if (field === 'status') {
      this.statusFilter.set(value ? [value] : []);
    }
    this.spacesDashboardUtils.onFilter(field, value);
  }

  /** Toggles the "Modified Only" filter (client-side overlay). */
  onModifiedOnlyChange(value: boolean): void {
    this.modifiedOnlyFilter.set(value);
  }

  /**
   * Handles a PrimeNG header sort event by mapping its field to a criterion.
   * Maps the legacy 'piiCount' header to the 'severityScore' criterion.
   */
  onCustomSort(event: SortEvent): void {
    if (!event.field) {
      this.sortCriterion.set(null);
      this.sortField.set(null);
      this.sortOrder.set(1);
      return;
    }
    this.sortField.set(event.field);
    this.sortOrder.set(event.order ?? 1);
    this.sortCriterion.set(event.field === 'piiCount' ? 'severityScore' : event.field);
  }

  /** Sets the active sort criterion (and resets the legacy field). */
  setSortCriterion(criterion: string | null, order: number = -1): void {
    this.sortCriterion.set(criterion);
    this.sortOrder.set(order);
    this.sortField.set(null);
  }

  /** Toggles the ascending/descending order. */
  toggleSortOrder(): void {
    this.sortOrder.update(o => (o === 1 ? -1 : 1));
  }

  /** Resets all filter, search and sort state (triggers a fresh server fetch). */
  reset(): void {
    this.globalFilter.set('');
    this.piiTypeFilter.set([]);
    this.severityFilter.set([]);
    this.statusFilter.set([]);
    this.modifiedOnlyFilter.set(false);
    this.sortCriterion.set(null);
    this.sortField.set(null);
    this.sortOrder.set(1);
    this.spacesDashboardUtils.globalFilter.set('');
    this.spacesDashboardUtils.onFilter('status', null);
  }
}
