import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SpaceFilteringService } from './space-filtering.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';
import { SpaceDataManagementService } from './space-data-management.service';
import { PiiDetectionConfigService } from '../../../core/services/pii-detection-config.service';
import {
  DashboardFacets,
  ScanReportingSummaryDto,
  SentinelleApiService
} from '../../../core/services/sentinelle-api.service';
import { PiiTypeConfig } from '../../../core/models/pii-detection-config.model';

const piiConfigs: PiiTypeConfig[] = [
  { id: 1, piiType: 'EMAIL', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
  { id: 2, piiType: 'PHONE_NUMBER', detector: 'PRESIDIO', enabled: true, threshold: 0.5, category: 'CONTACT' },
  { id: 3, piiType: 'IBAN_CODE', detector: 'REGEX', enabled: true, threshold: 0.5, category: 'FINANCIAL' }
];

/** Resolves after the service's debounced (200ms) server fetch has run. */
const flushFetch = (): Promise<void> => new Promise(resolve => setTimeout(resolve, 320));

/** Builds a server response with the given ordered keys (+ optional facets/total). */
function summaryResponse(keys: string[], facets?: DashboardFacets, total?: number): ScanReportingSummaryDto {
  return {
    scanId: 'scan-1',
    lastUpdated: '2026-06-24T00:00:00Z',
    spacesCount: total ?? keys.length,
    spaces: keys.map(k => ({
      spaceKey: k,
      status: 'OK',
      progressPercentage: null,
      pagesDone: 0,
      attachmentsDone: 0,
      lastEventAt: '',
      severityCounts: null
    })),
    facets: facets ?? { piiTypes: {}, severities: {}, statuses: {} }
  };
}

describe('SpaceFilteringService (server-driven)', () => {
  const updateInfo = signal<{ spaceKey: string; hasBeenUpdated: boolean }[]>([]);
  let api: { getDashboardSpacesSummary: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    updateInfo.set([]);
    api = { getDashboardSpacesSummary: vi.fn(() => of(summaryResponse(['A', 'B', 'C']))) };
    TestBed.configureTestingModule({
      providers: [
        SpaceFilteringService,
        SpacesDashboardUtils,
        { provide: SpaceDataManagementService, useValue: { spacesUpdateInfo: updateInfo } },
        { provide: PiiDetectionConfigService, useValue: { getAllPiiTypeConfigs: () => of(piiConfigs) } },
        { provide: SentinelleApiService, useValue: api }
      ]
    });
  });

  /** Injects the service and seeds the live store with three spaces. */
  function setup(): { service: SpaceFilteringService; utils: SpacesDashboardUtils } {
    const service = TestBed.inject(SpaceFilteringService);
    const utils = TestBed.inject(SpacesDashboardUtils);
    utils.setSpaces([{ key: 'A', name: 'Alpha' }, { key: 'B', name: 'Bravo' }, { key: 'C', name: 'Charlie' }]);
    return { service, utils };
  }

  it('Should_RenderStoreSpacesInServerOrder_When_ServerReturnsKeys', async () => {
    const { service } = setup();
    api.getDashboardSpacesSummary.mockReturnValue(of(summaryResponse(['C', 'A'])));
    service.piiTypeFilter.set(['EMAIL']); // triggers a criteria change -> fetch
    await flushFetch();
    expect(service.sortedSpaces().map(s => s.key)).toEqual(['C', 'A']);
  });

  it('Should_SendFilterCriteriaToBackend_When_FiltersAndSortChange', async () => {
    const { service } = setup();
    service.piiTypeFilter.set(['EMAIL']);
    service.statusFilter.set(['RUNNING']);
    service.setSortCriterion('severityScore'); // default order desc
    await flushFetch();
    const lastArg = api.getDashboardSpacesSummary.mock.calls.at(-1)?.[0];
    expect(lastArg.piiTypes).toEqual(['EMAIL']);
    expect(lastArg.statuses).toEqual(['RUNNING']);
    expect(lastArg.sort).toBe('severityScore');
    expect(lastArg.order).toBe('desc');
  });

  it('Should_ExposeServerFacetsAndTotal_When_ResponseHasFacets', async () => {
    const { service } = setup();
    const facets: DashboardFacets = {
      piiTypes: { EMAIL: { spaceCount: 1, totalOccurrences: 2 } },
      severities: {},
      statuses: {}
    };
    api.getDashboardSpacesSummary.mockReturnValue(of(summaryResponse(['A'], facets, 3)));
    service.severityFilter.set(['HIGH']);
    await flushFetch();
    expect(service.piiTypeFacetCounts()['EMAIL']).toEqual({ spaceCount: 1, totalOccurrences: 2 });
    expect(service.totalSpacesCount()).toBe(3);
  });

  it('Should_ApplyModifiedOnlyOverlay_When_ToggleEnabled', async () => {
    const { service } = setup();
    await flushFetch(); // initial fetch -> orderedKeys A,B,C
    updateInfo.set([{ spaceKey: 'B', hasBeenUpdated: true }]);
    service.onModifiedOnlyChange(true);
    expect(service.sortedSpaces().map(s => s.key)).toEqual(['B']);
  });

  it('Should_ReportResettable_When_AnyAxisActive', () => {
    const { service } = setup();
    expect(service.isResettable()).toBe(false);
    service.severityFilter.set(['HIGH']);
    expect(service.isResettable()).toBe(true);
  });

  it('Should_ClearAllState_When_ResetCalled', () => {
    const { service } = setup();
    service.piiTypeFilter.set(['EMAIL']);
    service.statusFilter.set(['OK']);
    service.onGlobalChange('alp');
    service.setSortCriterion('name');

    service.reset();

    expect(service.piiTypeFilter()).toEqual([]);
    expect(service.statusFilter()).toEqual([]);
    expect(service.globalFilter()).toBe('');
    expect(service.sortCriterion()).toBeNull();
    expect(service.isResettable()).toBe(false);
  });

  it('Should_GroupPiiTypesByCategory_When_ConfigsLoaded', () => {
    const { service } = setup();
    const contact = service.piiTypeGroups().find(g => g.category === 'CONTACT');
    expect(contact?.items.map(i => i.code).sort()).toEqual(['EMAIL', 'PHONE_NUMBER']);
  });

  it('Should_RefetchWithUnchangedCriteria_When_ReloadIsCalled', async () => {
    const { service } = setup();
    service.piiTypeFilter.set(['EMAIL']);
    await flushFetch();
    const callsBefore = api.getDashboardSpacesSummary.mock.calls.length;

    service.reload();
    await flushFetch();

    expect(api.getDashboardSpacesSummary.mock.calls).toHaveLength(callsBefore + 1);
    // The reload counter drives deduplication only; it must not leak into the query string.
    expect(api.getDashboardSpacesSummary.mock.calls.at(-1)?.[0]).not.toHaveProperty('reloadCounter');
  });

  it('Should_RecoverRowOrder_When_ReloadFollowsAFailedFetch', async () => {
    const { service } = setup();
    api.getDashboardSpacesSummary.mockReturnValue(throwError(() => new Error('backend down')));
    service.piiTypeFilter.set(['EMAIL']);
    await flushFetch();
    expect(service.sortedSpaces()).toEqual([]);

    api.getDashboardSpacesSummary.mockReturnValue(of(summaryResponse(['C', 'A'])));
    service.reload();
    await flushFetch();

    expect(service.sortedSpaces().map(s => s.key)).toEqual(['C', 'A']);
  });
});
