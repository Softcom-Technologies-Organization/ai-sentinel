import { computed, Injectable, inject, signal } from '@angular/core';
import { SortEvent } from 'primeng/api';
import { JiraProjectsDashboardUtils } from '../jira-projects-dashboard.utils';
import { JiraProjectDataManagementService } from './jira-project-data-management.service';
import { sortByFieldAndOrder } from '../../../shared/utils/dashboard-sort.util';

@Injectable({
  providedIn: 'root'
})
export class JiraProjectFilteringService {
  private readonly dashboardUtils = inject(JiraProjectsDashboardUtils);
  private readonly dataManagement = inject(JiraProjectDataManagementService);

  readonly globalFilter = signal<string>('');
  readonly statusFilter = signal<string | null>(null);

  readonly sortField = signal<string | null>(null);
  readonly sortOrder = signal<number>(1);

  readonly filteredProjects = computed(() => {
    return this.dashboardUtils.filteredProjects();
  });

  readonly sortedProjects = computed(() =>
    sortByFieldAndOrder(this.filteredProjects(), this.sortField(), this.sortOrder())
  );

  readonly statusOptions = computed(() => {
    return this.dashboardUtils.statusOptions();
  });

  onGlobalChange(value: string): void {
    this.globalFilter.set(value);
    this.dashboardUtils.globalFilter.set(value);
  }

  onFilter(field: 'name' | 'status', value: string | null | undefined): void {
    if (field === 'status') {
      this.statusFilter.set(value ?? null);
    }
    this.dashboardUtils.onFilter(field, value);
  }

  onCustomSort(event: SortEvent): void {
    if (!event.field) {
      this.sortField.set(null);
      this.sortOrder.set(1);
      return;
    }
    this.sortField.set(event.field);
    this.sortOrder.set(event.order ?? 1);
  }

  reset(): void {
    this.globalFilter.set('');
    this.statusFilter.set(null);
    this.sortField.set(null);
    this.sortOrder.set(1);
    this.dashboardUtils.globalFilter.set('');
    this.onFilter('status', null);
  }
}
