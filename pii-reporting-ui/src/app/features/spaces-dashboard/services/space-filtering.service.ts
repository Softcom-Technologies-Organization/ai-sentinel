import {computed, inject, Injectable, signal} from '@angular/core';
import {SortEvent} from 'primeng/api';
import {SpacesDashboardUtils} from '../spaces-dashboard.utils';

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

  // Filter state
  readonly globalFilter = signal<string>('');
  readonly statusFilter = signal<string | null>(null);

  // Sort state
  readonly sortField = signal<string | null>(null);
  readonly sortOrder = signal<number>(1); // 1 for ascending, -1 for descending

  /**
   * Filtered spaces based on current filter criteria.
   * Delegates to SpacesDashboardUtils to ensure consistent decoration logic.
   */
  readonly filteredSpaces = computed(() => {
    return this.spacesDashboardUtils.filteredSpaces();
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
        const nameA = (a.name ?? '').toLowerCase();
        const nameB = (b.name ?? '').toLowerCase();
        compareValue = nameA.localeCompare(nameB);
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
    this.spacesDashboardUtils.globalFilter.set('');
    this.onFilter('status', null);
  }
}
