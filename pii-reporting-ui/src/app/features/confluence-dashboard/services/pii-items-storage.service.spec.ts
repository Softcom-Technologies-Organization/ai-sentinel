import { TestBed } from '@angular/core/testing';
import { vi, expect } from 'vitest';
import { PiiItemsStorageService } from './pii-items-storage.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';

describe('PiiItemsStorageService', () => {
  let service: PiiItemsStorageService;
  let utilsMock: { updateSpace: ReturnType<typeof vi.fn> };

  const createPayload = (overrides: Record<string, any> = {}) => ({
    scanId: 'scan-1',
    spaceKey: 'SPACE1',
    pageId: 'page-1',
    pageTitle: 'Test Page',
    pageUrl: 'https://example.com/page-1',
    emittedAt: '2026-01-01T00:00:00Z',
    isFinal: true,
    severity: 'HIGH',
    detectedPIIs: [
      { startPosition: 0, endPosition: 10, piiType: 'EMAIL', piiTypeLabel: 'Email', sensitiveValue: 'test@test.com', confidence: 0.95 }
    ],
    detectedPiiCountBySeverity: { high: 1, medium: 0, low: 0 },
    detectedPiiCountByType: { EMAIL: 1 },
    ...overrides
  });

  beforeEach(() => {
    utilsMock = { updateSpace: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        PiiItemsStorageService,
        { provide: SpacesDashboardUtils, useValue: utilsMock }
      ]
    });
    service = TestBed.inject(PiiItemsStorageService);
  });

  // ========== addPiiItemToSpace ==========

  it('Should_AddItem_When_ValidPayload', () => {
    const result = service.addPiiItemToSpace('SPACE1', createPayload() as any);

    expect(result).toBe(true);
    expect(service.itemsBySpace()['SPACE1']).toHaveLength(1);
    expect(service.itemsBySpace()['SPACE1'][0].pageId).toBe('page-1');
  });

  it('Should_SkipItem_When_NoEntities', () => {
    const result = service.addPiiItemToSpace('SPACE1', createPayload({ detectedPIIs: [] }) as any);

    expect(result).toBe(false);
    expect(service.itemsBySpace()['SPACE1']).toBeUndefined();
  });

  it('Should_SkipDuplicate_When_SamePageAndAttachment', () => {
    service.addPiiItemToSpace('SPACE1', createPayload() as any);
    const result = service.addPiiItemToSpace('SPACE1', createPayload() as any);

    expect(result).toBe(false);
    expect(service.itemsBySpace()['SPACE1']).toHaveLength(1);
  });

  it('Should_AddDistinctItems_When_DifferentPages', () => {
    service.addPiiItemToSpace('SPACE1', createPayload({ pageId: 'page-1' }) as any);
    service.addPiiItemToSpace('SPACE1', createPayload({ pageId: 'page-2' }) as any);

    expect(service.itemsBySpace()['SPACE1']).toHaveLength(2);
  });

  it('Should_NormalizeSeverity_When_UpperCase', () => {
    service.addPiiItemToSpace('SPACE1', createPayload({ severity: 'MEDIUM' }) as any);

    expect(service.itemsBySpace()['SPACE1'][0].severity).toBe('medium');
  });

  it('Should_DefaultToLow_When_NoSeverity', () => {
    service.addPiiItemToSpace('SPACE1', createPayload({ severity: undefined }) as any);

    expect(service.itemsBySpace()['SPACE1'][0].severity).toBe('low');
  });

  it('Should_MapEntities_When_PayloadHasDetectedPIIList', () => {
    service.addPiiItemToSpace('SPACE1', createPayload() as any);

    const item = service.itemsBySpace()['SPACE1'][0];
    expect(item.detectedPersonallyIdentifiableInformationList).toHaveLength(1);
    expect(item.detectedPersonallyIdentifiableInformationList[0].piiType).toBe('EMAIL');
    expect(item.detectedPersonallyIdentifiableInformationList[0].confidence).toBe(0.95);
  });

  it('Should_CapAt400_When_ExceedingLimit', () => {
    for (let i = 0; i < 410; i++) {
      service.addPiiItemToSpace('SPACE1', createPayload({ pageId: `page-${i}` }) as any);
    }

    expect(service.itemsBySpace()['SPACE1']).toHaveLength(400);
  });

  // ========== clearAllItems ==========

  it('Should_ClearAll_When_ClearAllItems', () => {
    service.addPiiItemToSpace('SPACE1', createPayload() as any);
    service.addPiiItemToSpace('SPACE2', createPayload({ pageId: 'page-2', spaceKey: 'SPACE2' }) as any);

    service.clearAllItems();

    expect(service.itemsBySpace()).toEqual({});
    expect(utilsMock.updateSpace).toHaveBeenCalledWith('SPACE1', { counts: { total: 0, high: 0, medium: 0, low: 0 } });
    expect(utilsMock.updateSpace).toHaveBeenCalledWith('SPACE2', { counts: { total: 0, high: 0, medium: 0, low: 0 } });
  });

  // ========== clearItemsForSpace ==========

  it('Should_ClearSingleSpace_When_ClearItemsForSpace', () => {
    service.addPiiItemToSpace('SPACE1', createPayload() as any);
    service.addPiiItemToSpace('SPACE2', createPayload({ pageId: 'page-2', spaceKey: 'SPACE2' }) as any);

    service.clearItemsForSpace('SPACE1');

    expect(service.itemsBySpace()['SPACE1']).toBeUndefined();
    expect(service.itemsBySpace()['SPACE2']).toBeDefined();
  });

  it('Should_DoNothing_When_ClearNonexistentSpace', () => {
    service.clearItemsForSpace('NONEXISTENT');
    expect(utilsMock.updateSpace).not.toHaveBeenCalled();
  });
});
