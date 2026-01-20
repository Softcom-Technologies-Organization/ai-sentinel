import { computed, inject, Injectable, signal } from '@angular/core';
import { SortEvent } from 'primeng/api';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';
import { SpaceDataManagementService } from './space-data-management.service';

/**
 * Service responsible for filtering and sorting spaces in the dashboard.
 *
 * Business purpose:
 * - Provides reactive filtering by global search term and status
 * - Handles custom sorting by name and PII count
 * - Maintains filter state independently for reusability
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles filtering and sorting logic
 * - Open/Closed: Can be extended with new filter types without modification
 * - Dependency Inversion: Depends on SpacesDashboardUtils abstraction
 */
@Injectable({
  providedIn: 'root'
})
export class SpaceFilteringService {
  private readonly spacesDashboardUtils = inject(SpacesDashboardUtils);
  private readonly dataManagement = inject(SpaceDataManagementService);

  // Filter state
  readonly globalFilter = signal<string>('');
  readonly statusFilter = signal<string | null>(null);
  readonly modifiedOnlyFilter = signal<boolean>(false);

  // Sort state
  readonly sortField = signal<string | null>(null);
  readonly sortOrder = signal<number>(1); // 1 for ascending, -1 for descending

  /**
   * Filtered spaces based on current filter criteria.
   * Delegates to SpacesDashboardUtils to ensure consistent decoration logic.
   * Applies "Modified Only" filter if enabled.
   */
  readonly filteredSpaces = computed(() => {
    let spaces = this.spacesDashboardUtils.filteredSpaces();

    if (this.modifiedOnlyFilter()) {
      const updateInfos = this.dataManagement.spacesUpdateInfo();
      spaces = spaces.filter(s => {
        const info = updateInfos.find(i => i.spaceKey === s.key);
        return info?.hasBeenUpdated ?? false;
      });
    }

    return spaces;
  });

  /**
   * Sorted spaces based on current sort field and order.
   * Supports sorting by:
   * - name: alphabetical order
   * - piiCount: by priority - high first, then medium, then low
   */
  readonly sortedSpaces = computed(() => {
    const spaces = [...this.filteredSpaces()];
    const field = this.sortField();
    const order = this.sortOrder();

    if (!field) {
      return spaces;
    }

    return spaces.sort((a, b) => {
      let compareValue = 0;

      if (field === 'name') {
        // Aligner le tri "par nom" avec l'ordre Confluence (ordre backend)
        // en s'appuyant sur l'index d'origine fourni par le backend.
        // Cela garantit que le tri par défaut reflète exactement l'ordre Confluence.
        const idxA = (a as any).originalIndex ?? 0;
        const idxB = (b as any).originalIndex ?? 0;
        compareValue = idxA - idxB;
      } else if (field === 'piiCount') {
        // Sort by priority: high > medium > low (descending for each)
        const priorities = ['high', 'medium', 'low'] as const;

        for (const priority of priorities) {
          const countA = a.counts?.[priority] ?? 0;
          const countB = b.counts?.[priority] ?? 0;

          if (countA !== countB) {
            compareValue = countB - countA;
            break;
          }
        }
      }

      return compareValue * order;
    });
  });

  /**
   * Available status options for filtering.
   */
  readonly statusOptions = computed(() => {
    return this.spacesDashboardUtils.statusOptions();
  });

  /**
   * Updates the global search filter.
   * Also synchronizes with SpacesDashboardUtils for consistency.
   */
  onGlobalChange(value: string): void {
    this.globalFilter.set(value);
    this.spacesDashboardUtils.globalFilter.set(value);
  }

  /**
   * Updates a specific filter field.
   * Delegates to SpacesDashboardUtils for the actual filtering logic.
   */
  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    if (field === 'status') {
      this.statusFilter.set(value ?? null);
    }
    this.spacesDashboardUtils.onFilter(field, value);
  }

  /**
   * Toggles the "Modified Only" filter.
   */
  onModifiedOnlyChange(value: boolean): void {
    this.modifiedOnlyFilter.set(value);
  }

  /**
   * Handles custom sort event from PrimeNG table.
   * Updates sort field and order signals to trigger sortedSpaces() recomputation.
   */
  onCustomSort(event: SortEvent): void {
    if (!event.field) {
      this.sortField.set(null);
      this.sortOrder.set(1);
      return;
    }

    this.sortField.set(event.field);
    this.sortOrder.set(event.order ?? 1);
  }

  /**
   * Resets all filters and sorting to their default state.
   */
  reset(): void {
    this.globalFilter.set('');
    this.statusFilter.set(null);
    this.sortField.set(null);
    this.sortOrder.set(1);
    this.modifiedOnlyFilter.set(false);
    this.spacesDashboardUtils.globalFilter.set('');
    this.onFilter('status', null);
  }
}
