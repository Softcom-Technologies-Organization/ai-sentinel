import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TableModule } from 'primeng/table';
import { SpaceFilteringService } from './services/space-filtering.service';
import { SpacesDashboardUtils } from './spaces-dashboard.utils';
import { SpaceDataManagementService } from './services/space-data-management.service';
import { PiiDetectionConfigService } from '../../core/services/pii-detection-config.service';
import { ScanReportingSummaryDto, SentinelleApiService } from '../../core/services/sentinelle-api.service';

/** Resolves after the filtering service's debounced (200ms) server fetch has run. */
const flushFetch = (): Promise<void> => new Promise(resolve => setTimeout(resolve, 320));

function summaryResponse(keys: string[]): ScanReportingSummaryDto {
  return {
    scanId: 'scan-1',
    lastUpdated: '2026-06-24T00:00:00Z',
    spacesCount: keys.length,
    spaces: keys.map(k => ({
      spaceKey: k,
      status: 'OK',
      progressPercentage: null,
      pagesDone: 0,
      attachmentsDone: 0,
      lastEventAt: '',
      severityCounts: null
    })),
    facets: { piiTypes: {}, severities: {}, statuses: {} }
  };
}

/**
 * Mirrors the p-table sort bindings of confluence-dashboard.component.html.
 * They must stay identical: PrimeNG re-emits (onSort) with its own sortField /
 * sortOrder every time [value] changes, so any sort state the table holds on its
 * own is written back over the filter state on each data refresh.
 */
@Component({
  standalone: true,
  imports: [TableModule],
  template: `
    <p-table [value]="filtering.sortedSpaces()"
             [sortField]="filtering.tableSortField()" [sortOrder]="filtering.sortOrder()"
             (onSort)="filtering.onCustomSort($event)" [customSort]="true">
      <ng-template #body let-space>
        <tr><td>{{ space.key }}</td></tr>
      </ng-template>
    </p-table>
  `
})
class DashboardTableHost {
  readonly filtering = inject(SpaceFilteringService);
}

describe('Confluence dashboard table sort', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardTableHost],
      providers: [
        SpaceFilteringService,
        SpacesDashboardUtils,
        { provide: SpaceDataManagementService, useValue: { spacesUpdateInfo: () => [] } },
        { provide: PiiDetectionConfigService, useValue: { getAllPiiTypeConfigs: () => of([]) } },
        {
          provide: SentinelleApiService,
          useValue: { getDashboardSpacesSummary: vi.fn(() => of(summaryResponse(['A', 'B']))) }
        }
      ]
    });
  });

  it('Should_KeepTheChosenSort_When_TheRowsAreRefreshed', async () => {
    const fixture = TestBed.createComponent(DashboardTableHost);
    const filtering = TestBed.inject(SpaceFilteringService);
    const utils = TestBed.inject(SpacesDashboardUtils);
    utils.setSpaces([{ key: 'A', name: 'Alpha' }, { key: 'B', name: 'Bravo' }]);
    await flushFetch();
    fixture.detectChanges();

    filtering.setSortCriterion('totalDetections');
    await flushFetch();
    fixture.detectChanges();

    // A live scan update replaces the row objects, which re-triggers the table sort.
    utils.setSpaces([{ key: 'A', name: 'Alpha' }, { key: 'B', name: 'Bravo' }]);
    fixture.detectChanges();

    expect(filtering.sortCriterion()).toBe('totalDetections');
    expect(filtering.sortOrder()).toBe(-1);
  });
});
