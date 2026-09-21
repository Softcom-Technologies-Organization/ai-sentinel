import { describe, expect, it } from 'vitest';
import { ObfuscationViewStateService } from './obfuscation-view-state.service';
import { RemediationFindingsSearchResponse } from '../../../core/models/remediation.model';

describe('ObfuscationViewStateService', () => {
  function createService(): ObfuscationViewStateService {
    return new ObfuscationViewStateService();
  }

  it('Should_ExposeDefaults_When_Created', () => {
    const service = createService();

    expect(service.groupBy()).toBe('type');
    expect(service.openAccordions().size).toBe(0);
    expect(service.page()).toBe(0);
    expect(service.pageSize()).toBe(20);
    expect(service.statusFilter()).toBe('ALL');
    expect(service.searchText()).toBe('');
    expect(service.itemFilter()).toBeNull();
    expect(service.lastSearchResponse()).toBeNull();
    expect(service.lastPlan()).toBeNull();
    expect(service.loading()).toBe(false);
  });

  it('Should_StoreBackendPlanVerbatim_When_PlanSet', () => {
    const service = createService();
    const plan = {
      totalFindings: 7,
      bySeverity: { high: 3, low: 4 },
      pagesImpacted: 2,
      falsePositivesReported: 1,
      selectionChecksum: 'abc',
      attachmentExclusions: 0
    };

    service.lastPlan.set(plan);

    expect(service.lastPlan()).toBe(plan);
  });

  it('Should_UpdateFilters_When_SignalsSet', () => {
    const service = createService();

    service.groupBy.set('severity');
    service.page.set(3);
    service.pageSize.set(50);
    service.statusFilter.set('PENDING');
    service.searchText.set('john');
    service.itemFilter.set('page-1');

    expect(service.groupBy()).toBe('severity');
    expect(service.page()).toBe(3);
    expect(service.pageSize()).toBe(50);
    expect(service.statusFilter()).toBe('PENDING');
    expect(service.searchText()).toBe('john');
    expect(service.itemFilter()).toBe('page-1');
  });

  it('Should_ToggleAccordion_When_SameKeyToggledTwice', () => {
    const service = createService();

    service.toggleAccordion('EMAIL');
    expect(service.openAccordions().has('EMAIL')).toBe(true);

    service.toggleAccordion('EMAIL');
    expect(service.openAccordions().has('EMAIL')).toBe(false);
  });

  it('Should_OpenAllGivenKeys_When_OpenAllCalled', () => {
    const service = createService();

    service.openAll(['EMAIL', 'IBAN']);

    expect([...service.openAccordions()].sort((a, b) => a.localeCompare(b))).toEqual(['EMAIL', 'IBAN']);
  });

  it('Should_CloseEveryAccordion_When_CollapseAllCalled', () => {
    const service = createService();
    service.openAll(['EMAIL', 'IBAN']);

    service.collapseAll();

    expect(service.openAccordions().size).toBe(0);
  });

  it('Should_StoreBackendResponseVerbatim_When_SearchResponseSet', () => {
    const service = createService();
    const response: RemediationFindingsSearchResponse = {
      groups: [
        {
          key: 'EMAIL',
          label: 'Email',
          total: 12,
          occurrenceCount: 12,
          selectedCount: 4,
          masterState: 'partial',
          findings: []
        }
      ],
      totals: { pending: 10, handled: 1, falsePositive: 1, proposedFalsePositive: 0, total: 12 },
      page: 0,
      pageSize: 20,
      totalElements: 12,
      totalGroups: 1,
      nonEligibleLegacyCount: 0
    };

    service.lastSearchResponse.set(response);

    expect(service.lastSearchResponse()).toBe(response);
  });
});
