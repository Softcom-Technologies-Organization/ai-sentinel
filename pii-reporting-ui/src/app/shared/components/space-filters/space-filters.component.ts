import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TranslocoModule } from '@jsverse/transloco';

/**
 * Presentational component for filtering spaces in the dashboard.
 *
 * Business purpose:
 * Provides user controls to filter the spaces list by:
 * - Global search (text input) - searches across space names
 * - Status filter (dropdown) - filters by scan status (RUNNING, OK, FAILED, etc.)
 *
 * This component is purely presentational and emits filter changes to parent.
 * The parent component (SpacesDashboardComponent) is responsible for applying
 * the filters to the spaces list.
 *
 * Design decisions:
 * - OnPush change detection for optimal performance
 * - Standalone component for easy reusability
 * - Two-way binding support via @Input/@Output pairs
 * - Keyboard accessible (native input/select behavior)
 * - PrimeNG components for consistent UI
 * - Transloco for i18n support
 *
 * @example
 * ```html
 * <app-space-filters
 *   [globalFilter]="globalFilter()"
 *   [statusFilter]="statusFilter()"
 *   [statusOptions]="statusOptions"
 *   (globalFilterChange)="onGlobalChange($event)"
 *   (statusFilterChange)="onFilter('status', $event)">
 * </app-space-filters>
 * ```
 */
@Component({
  selector: 'app-space-filters',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    SelectModule,
    TranslocoModule
  ],
  templateUrl: './space-filters.component.html',
  styleUrls: ['./space-filters.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpaceFiltersComponent {
  /**
   * Current value of the global search filter.
   * Searches across space names.
   */
  @Input() globalFilter: string = '';

  /**
   * Current value of the status filter.
   * Null indicates no status filter is applied (all statuses shown).
   */
  @Input() statusFilter: string | null = null;

  /**
   * Available status options for the dropdown filter.
   * Each option should have:
   * - labelKey: Translation key for the status label
   * - value: Status value used for filtering
   *
   * @example
   * ```typescript
   * [
   *   { labelKey: 'dashboard.status.running', value: 'RUNNING' },
   *   { labelKey: 'dashboard.status.ok', value: 'OK' }
   * ]
   * ```
   */
  @Input() statusOptions: Array<{ labelKey: string; value: string }> = [];

  /**
   * Emits when the global search filter value changes.
   * Parent should update its filter state and recompute filtered spaces.
   */
  @Output() globalFilterChange = new EventEmitter<string>();

  /**
   * Emits when the status filter value changes.
   * Null is emitted when the filter is cleared.
   * Parent should update its filter state and recompute filtered spaces.
   */
  @Output() statusFilterChange = new EventEmitter<string | null>();

  /**
   * Handles global search input changes.
   * Emits the new filter value to the parent component.
   *
   * @param value - New search term entered by user
   */
  onGlobalFilterChange(value: string): void {
    this.globalFilterChange.emit(value);
  }

  /**
   * Handles status filter selection changes.
   * Emits the new status value or null if filter is cleared.
   *
   * @param value - Selected status value or null if cleared
   */
  onStatusFilterChange(value: string | null): void {
    this.statusFilterChange.emit(value);
  }
}
