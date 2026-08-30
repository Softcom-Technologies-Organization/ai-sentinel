import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { DashboardUiStateService } from './dashboard-ui-state.service';
import { TranslocoService } from '@jsverse/transloco';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';

describe('DashboardUiStateService', () => {
  let service: DashboardUiStateService;
  let translocoMock: { translate: ReturnType<typeof vi.fn> };
  let utilsMock: { statusStyle: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    translocoMock = { translate: vi.fn((key: string) => key) };
    utilsMock = { statusStyle: vi.fn().mockReturnValue('success') };

    TestBed.configureTestingModule({
      providers: [
        DashboardUiStateService,
        { provide: TranslocoService, useValue: translocoMock },
        { provide: SpacesDashboardUtils, useValue: utilsMock }
      ]
    });
    service = TestBed.inject(DashboardUiStateService);
  });

  // ========== Row Expansion ==========

  it('Should_ExpandRow_When_OnRowExpand', () => {
    service.onRowExpand({ data: { key: 'SPACE1' } });

    expect(service.expandedRowKeys()).toEqual({ SPACE1: true });
    expect(service.selectedSpaceKey()).toBe('SPACE1');
  });

  it('Should_DoNothing_When_OnRowExpandWithNoKey', () => {
    service.onRowExpand({ data: {} });
    service.onRowExpand({});

    expect(service.expandedRowKeys()).toEqual({});
  });

  it('Should_CollapseRow_When_OnRowCollapse', () => {
    service.expandRow('SPACE1');
    service.expandRow('SPACE2');

    service.onRowCollapse({ data: { key: 'SPACE1' } });

    expect(service.expandedRowKeys()).toEqual({ SPACE2: true });
  });

  it('Should_DoNothing_When_OnRowCollapseWithNoKey', () => {
    service.expandRow('SPACE1');
    service.onRowCollapse({ data: {} });

    expect(service.expandedRowKeys()).toEqual({ SPACE1: true });
  });

  it('Should_ExpandRowProgrammatically_When_ExpandRow', () => {
    service.expandRow('SPACE1');

    expect(service.expandedRowKeys()).toEqual({ SPACE1: true });
  });

  it('Should_NotDuplicate_When_ExpandRowCalledTwice', () => {
    service.expandRow('SPACE1');
    service.expandRow('SPACE1');

    expect(service.expandedRowKeys()).toEqual({ SPACE1: true });
  });

  it('Should_CollapseRowProgrammatically_When_CollapseRow', () => {
    service.expandRow('SPACE1');
    service.collapseRow('SPACE1');

    expect(service.expandedRowKeys()).toEqual({});
  });

  it('Should_CollapseAllRows_When_CollapseAllRows', () => {
    service.expandRow('SPACE1');
    service.expandRow('SPACE2');

    service.collapseAllRows();

    expect(service.expandedRowKeys()).toEqual({});
  });

  it('Should_ReturnExpandedCount_When_ExpandedRowCount', () => {
    service.expandRow('A');
    service.expandRow('B');

    expect(service.expandedRowCount()).toBe(2);
  });

  // ========== Selection ==========

  it('Should_SetSelectedSpaceKey_When_SelectSpace', () => {
    service.selectSpace('SPACE1');
    expect(service.selectedSpaceKey()).toBe('SPACE1');
    expect(service.hasSelectedSpace()).toBe(true);

    service.selectSpace(null);
    expect(service.selectedSpaceKey()).toBeNull();
    expect(service.hasSelectedSpace()).toBe(false);
  });

  // ========== Log Lines ==========

  it('Should_AppendLogLine_When_Append', () => {
    service.append('Line 1');
    service.append('Line 2');

    expect(service.lines()).toEqual(['Line 1', 'Line 2']);
  });

  it('Should_TrimOldestLines_When_OverMaxLimit', () => {
    for (let i = 0; i < 1002; i++) {
      service.append(`Line ${i}`);
    }

    expect(service.lines()).toHaveLength(1000);
    expect(service.lines()[0]).toBe('Line 2');
  });

  it('Should_ClearAllLogs_When_ClearLogs', () => {
    service.append('Line 1');
    service.clearLogs();

    expect(service.lines()).toEqual([]);
  });

  // ========== Scan History ==========

  it('Should_InsertNewEntry_When_UpsertScanHistoryNewSpace', () => {
    service.upsertScanHistory('SPACE1', 'running');

    expect(service.history()).toEqual([{ spaceKey: 'SPACE1', status: 'running' }]);
  });

  it('Should_UpdateExistingEntry_When_UpsertScanHistoryExistingSpace', () => {
    service.upsertScanHistory('SPACE1', 'running');
    service.upsertScanHistory('SPACE1', 'completed');

    expect(service.history()).toEqual([{ spaceKey: 'SPACE1', status: 'completed' }]);
  });

  it('Should_ReturnEntry_When_GetScanHistory', () => {
    service.upsertScanHistory('SPACE1', 'running');

    expect(service.getScanHistory('SPACE1')).toEqual({ spaceKey: 'SPACE1', status: 'running' });
    expect(service.getScanHistory('NONE')).toBeUndefined();
  });

  it('Should_ClearAllHistory_When_ClearHistory', () => {
    service.upsertScanHistory('SPACE1', 'running');
    service.clearHistory();

    expect(service.history()).toEqual([]);
  });

  // ========== Status Label / Style ==========

  it('Should_TranslateStatusKey_When_StatusLabel', () => {
    service.statusLabel('RUNNING');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.running');

    service.statusLabel('OK');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.ok');

    service.statusLabel('FAILED');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.failed');

    service.statusLabel('PENDING');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.pending');

    service.statusLabel('NOT_STARTED');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.notStarted');

    service.statusLabel('PAUSED');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.paused');

    service.statusLabel('INTERRUPTED');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.interrupted');

    service.statusLabel('COMPLETED');
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.completed');

    service.statusLabel(undefined);
    expect(translocoMock.translate).toHaveBeenCalledWith('dashboard.status.notStarted');
  });

  it('Should_DelegateToUtils_When_StatusStyle', () => {
    service.statusStyle('RUNNING');
    expect(utilsMock.statusStyle).toHaveBeenCalledWith('RUNNING');
  });

  // ========== Reset ==========

  it('Should_ResetAllState_When_Reset', () => {
    service.expandRow('SPACE1');
    service.selectSpace('SPACE1');
    service.append('Line 1');
    service.upsertScanHistory('SPACE1', 'running');
    service.activeSpaceKey.set('SPACE1');
    service.selectedSpaces.set([{ key: 'SPACE1' }]);

    service.reset();

    expect(service.expandedRowKeys()).toEqual({});
    expect(service.selectedSpaceKey()).toBeNull();
    expect(service.activeSpaceKey()).toBeNull();
    expect(service.selectedSpaces()).toEqual([]);
    expect(service.lines()).toEqual([]);
    expect(service.history()).toEqual([]);
  });
});
