import {computed, Injectable, signal} from '@angular/core';
import {Space} from '../../core/models/space';
import {
  PersonallyIdentifiableInformationScanResult
} from '../../core/models/personally-identifiable-information-scan-result';

/**
 * Facade for Spaces Dashboard UI concerns.
 *
 * Business intent: expose a filtered list of spaces and UI helpers for table rendering
 * without leaking logic into the component. The component only delegates and binds signals.
 */
export interface UISpace extends Space {
  lastScanTs?: string;
  counts?: { total: number; high: number; medium: number; low: number };
  url?: string;
}

@Injectable({ providedIn: 'root' })
export class SpacesDashboardUtils {

  // raw ui list populated from backend spaces with safe defaults for display fields
  private readonly uiSpaces = signal<UISpace[]>([]);

  // filters
  readonly globalFilter = signal<string>('');
  private readonly filters = signal<{ name?: string; status?: string | null }>({});

  /**
   * Status options for dropdown filter with i18n support.
   * The translation is handled in the template using transloco pipe.
   */
  readonly statusOptions = signal<Array<{ labelKey: string; value: string }>>([
    { labelKey: 'dashboard.status.pending', value: 'PENDING' },
    { labelKey: 'dashboard.status.paused', value: 'PAUSED' },
    { labelKey: 'dashboard.status.running', value: 'RUNNING' },
    { labelKey: 'dashboard.status.ok', value: 'OK' },
    { labelKey: 'dashboard.status.failed', value: 'FAILED' }
  ]);

  /**
   * UI-facing, filtered list of spaces according to current filters.
   */
  readonly filteredSpaces = computed(() => {
    const g = (this.globalFilter() ?? '').toLowerCase();
    const { name = '', status = null } = this.filters();
    return this.uiSpaces().filter(s =>
      (g ? (s.name ?? '').toLowerCase().includes(g) || s.key.toLowerCase().includes(g) : true) &&
      (name ? (s.name ?? '').toLowerCase().includes(name.toLowerCase()) : true) &&
      (status ? s.status === status : true)
    );
  });

  /**
   * Replace spaces with UI decorated version for table display.
   */
  setSpaces(spaces: Space[] | null | undefined): void {
    const mapped: UISpace[] = (spaces ?? []).map<UISpace>(s => ({
      ...s,
      status: 'PENDING',
      lastScanTs: undefined,
      counts: { total: 0, high: 0, medium: 0, low: 0 },
      // Preserve backend-provided URL when present
      url: s.url
    }));
    this.uiSpaces.set(mapped);
  }

  /** Update a single UI space by key (case/whitespace-insensitive). */
  updateSpace(key: string, patch: Partial<UISpace>): void {
    const list = this.uiSpaces();
    const nk = (v: string | null | undefined) => String(v ?? '').trim().toLowerCase();
    const k = nk(key);
    const updated = list.map(s => (nk(s.key) === k ? { ...s, ...patch } : s));
    this.uiSpaces.set(updated);
  }

  onFilter(field: 'name' | 'status', value: string | null = null): void {
    this.filters.set({ ...this.filters(), [field]: value });
  }

  statusLabel(status?: string): string {
    // Normalize to business-friendly French labels
    if (status === 'FAILED') return 'En échec';
    if (status === 'RUNNING') return 'En cours';
    if (status === 'PAUSED') return 'En pause';
    if (status === 'PENDING' || !status) return 'Non démarré';
    return 'Terminé';
  }

  statusStyle(status?: string): 'danger' | 'warning' | 'success' | 'info' | 'secondary' {
    // Use PrimeNG-supported severities for Tag: success | info | warning | danger | secondary
    if (status === 'FAILED') return 'danger';
    if (status === 'RUNNING') return 'warning';
    if (status === 'PAUSED') return 'info';
    if (status === 'PENDING' || !status) return 'secondary';
    return 'success';
  }

  /**
   * Additional style class for status badge.
   * Business rule: highlight running scan ("En cours") with a dedicated blue tone
   * and paused scan ("En pause") with a distinct palette.
   */
  statusStyleClass(status?: string): string | undefined {
    if (status === 'RUNNING') {
      return 'status-running';
    }
    if (status === 'PAUSED') {
      return 'status-paused';
    }
    return undefined;
  }

  canViewResult(space: UISpace): boolean {
    // Allow viewing results while scan is running (partial results), or when a previous scan exists
    return space.status === 'RUNNING' || !!space.lastScanTs;
  }

  /**
   * Opens the Confluence space in a new tab if a URL is available on the UI model.
   * Business rule: do nothing when URL is unknown to avoid sending users to a wrong location.
   */
  openConfluence(space: UISpace): void {
    const url = space?.url;
    if (!url) return;
    window.open(url, '_blank', 'noopener');
  }

  /** True when a Confluence URL is configured/known for this space. */
  hasConfluenceUrl(space: UISpace | null | undefined): boolean {
    return !!space?.url && space.url.length > 0;
  }

  /**
   * Get current severity counts for a space.
   * Returns default zero counts if space not found.
   */
  getSpaceCounts(key: string): { total: number; high: number; medium: number; low: number } {
    const nk = (v: string | null | undefined) => String(v ?? '').trim().toLowerCase();
    const k = nk(key);
    const space = this.uiSpaces().find(s => nk(s.key) === k);
    return space?.counts ?? { total: 0, high: 0, medium: 0, low: 0 };
  }

  severityCounts(arr: PersonallyIdentifiableInformationScanResult[]): { total: number; high: number; medium: number; low: number } {
    let high = 0, medium = 0, low = 0;
    for (const it of arr) {
      if (it.severity === 'high') high++;
      else if (it.severity === 'medium') medium++;
      else low++;
    }
    return { total: arr.length, high, medium, low };
  }
}
