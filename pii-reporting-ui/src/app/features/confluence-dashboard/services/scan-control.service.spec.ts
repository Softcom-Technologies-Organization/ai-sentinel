import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { vi, expect } from 'vitest';
import { ScanControlService } from './scan-control.service';
import { SentinelleApiService } from '../../../core/services/sentinelle-api.service';
import { ScanStatusPollingService } from '../../../core/services/scan-status-polling.service';
import { ConfirmationService } from 'primeng/api';
import { TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast.service';
import { ScanProgressService } from '../../../core/services/scan-progress.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';
import { PiiItemsStorageService } from './pii-items-storage.service';
import { DashboardUiStateService } from './dashboard-ui-state.service';
import { SpaceDataManagementService } from './space-data-management.service';
import { SseEventHandlerService } from './sse-event-handler.service';
import { of, Subject, throwError } from 'rxjs';

describe('ScanControlService', () => {
  let service: ScanControlService;

  let apiMock: Record<string, ReturnType<typeof vi.fn>>;
  let pollingMock: {
    scanActive: ReturnType<typeof signal>;
    scanPaused: ReturnType<typeof signal>;
    actionPending: ReturnType<typeof signal>;
    scanCompleted$: Subject<void>;
    start: ReturnType<typeof vi.fn>;
    stop: ReturnType<typeof vi.fn>;
    forceRefresh: ReturnType<typeof vi.fn>;
  };
  let confirmMock: { confirm: ReturnType<typeof vi.fn> };
  let translocoMock: { translate: ReturnType<typeof vi.fn> };
  let toastMock: { clearScanErrors: ReturnType<typeof vi.fn> };
  let progressMock: Record<string, ReturnType<typeof vi.fn>>;
  let utilsMock: Record<string, ReturnType<typeof vi.fn>>;
  let storageMock: Record<string, ReturnType<typeof vi.fn>>;
  let uiStateMock: Record<string, any>;
  let dataManagementMock: Record<string, any>;
  let sseMock: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    apiMock = {
      purgeAllScans: vi.fn().mockReturnValue(of(undefined)),
      startAllSpacesStream: vi.fn().mockReturnValue(of()),
      startSelectedSpacesStream: vi.fn().mockReturnValue(of()),
      pauseScan: vi.fn().mockReturnValue(of(undefined)),
      resumeScan: vi.fn().mockReturnValue(of(undefined)),
      getLastScanItems: vi.fn().mockReturnValue(of([]))
    };

    pollingMock = {
      scanActive: signal(false),
      scanPaused: signal(false),
      actionPending: signal(false),
      scanCompleted$: new Subject<void>(),
      start: vi.fn(),
      stop: vi.fn(),
      forceRefresh: vi.fn().mockResolvedValue(undefined)
    };

    confirmMock = { confirm: vi.fn() };
    translocoMock = { translate: vi.fn((key: string) => key) };
    toastMock = { clearScanErrors: vi.fn() };
    progressMock = { resetProgress: vi.fn(), resetAllProgress: vi.fn() };
    utilsMock = { updateSpace: vi.fn(), setSpaces: vi.fn(), allSpaces: vi.fn().mockReturnValue([]) };
    storageMock = { clearItemsForSpace: vi.fn(), clearAllItems: vi.fn() };

    uiStateMock = {
      append: vi.fn(),
      collapseAllRows: vi.fn(),
      selectSpace: vi.fn(),
      activeSpaceKey: signal(null),
      selectedSpaceKey: signal(null),
      selectedSpaces: signal<{ key: string }[]>([]),
      clearHistory: vi.fn()
    };

    dataManagementMock = {
      spaces: signal([{ key: 'SPACE1' }, { key: 'SPACE2' }]),
      lastScanMeta: signal({ scanId: 'scan-1', lastUpdated: '', spacesCount: 2 }),
      lastSpaceStatuses: vi.fn().mockReturnValue([]),
      currentScanSpaceKeys: signal(null),
      canStartScan: signal(true),
      queue: signal([]),
      loadLastSpaceStatuses: vi.fn().mockReturnValue(of(undefined)),
      loadLastScan: vi.fn().mockReturnValue(of(undefined)),
      loadLastItems: vi.fn().mockReturnValue(of(undefined))
    };

    sseMock = { routeStreamEvent: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        ScanControlService,
        { provide: SentinelleApiService, useValue: apiMock },
        { provide: ScanStatusPollingService, useValue: pollingMock },
        { provide: ConfirmationService, useValue: confirmMock },
        { provide: TranslocoService, useValue: translocoMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ScanProgressService, useValue: progressMock },
        { provide: SpacesDashboardUtils, useValue: utilsMock },
        { provide: PiiItemsStorageService, useValue: storageMock },
        { provide: DashboardUiStateService, useValue: uiStateMock },
        { provide: SpaceDataManagementService, useValue: dataManagementMock },
        { provide: SseEventHandlerService, useValue: sseMock }
      ]
    });
    service = TestBed.inject(ScanControlService);
  });

  // ========== Computed Signals ==========

  it('Should_ComputeCanStartScan_When_Idle', () => {
    expect(service.canStartScan()).toBe(true);
  });

  it('Should_DisableStart_When_ActionPending', () => {
    pollingMock.actionPending.set(true);
    expect(service.canStartScan()).toBe(false);
  });

  it('Should_DisableStart_When_ScanActive', () => {
    pollingMock.scanActive.set(true);
    expect(service.canStartScan()).toBe(false);
  });

  it('Should_ComputeCanPauseScan_When_ScanRunning', () => {
    pollingMock.scanActive.set(true);
    expect(service.canPauseScan()).toBe(true);
  });

  it('Should_DisablePause_When_NotRunning', () => {
    expect(service.canPauseScan()).toBe(false);
  });

  it('Should_ComputeCanResumeScan_When_Paused', () => {
    pollingMock.scanPaused.set(true);
    expect(service.canResumeScan()).toBe(true);
  });

  it('Should_DisableResume_When_NotPaused', () => {
    expect(service.canResumeScan()).toBe(false);
  });

  // ========== startAll ==========

  it('Should_ShowConfirmation_When_StartAll', () => {
    service.startAll();
    expect(confirmMock.confirm).toHaveBeenCalledTimes(1);
  });

  it('Should_NotStart_When_ScanAlreadyActive', () => {
    pollingMock.scanActive.set(true);
    service.startAll();
    expect(confirmMock.confirm).not.toHaveBeenCalled();
  });

  it('Should_NotStart_When_ActionPending', () => {
    pollingMock.actionPending.set(true);
    service.startAll();
    expect(confirmMock.confirm).not.toHaveBeenCalled();
  });

  it('Should_PurgeAndStream_When_ConfirmAccepted', () => {
    service.startAll();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.accept();

    // purgeAllScans is called, and its success callback triggers polling
    expect(apiMock.purgeAllScans).toHaveBeenCalled();
    // polling.start is called inside the purge success callback (observable emits synchronously with of())
    expect(pollingMock.start).toHaveBeenCalledWith(3000);
    expect(service.isStreaming()).toBe(true);
  });

  it('Should_LogCancellation_When_ConfirmRejected', () => {
    service.startAll();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.reject();

    expect(uiStateMock.append).toHaveBeenCalled();
  });

  // ========== startSelected ==========

  it('Should_ShowConfirmation_When_StartSelected', () => {
    service.startSelected(['SPACE1']);
    expect(confirmMock.confirm).toHaveBeenCalledTimes(1);
  });

  it('Should_NotStart_When_EmptySelection', () => {
    service.startSelected([]);
    expect(confirmMock.confirm).not.toHaveBeenCalled();
  });

  it('Should_StreamSelectedSpaces_When_ConfirmAccepted', () => {
    uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }, { key: 'SPACE2' }]);
    service.startSelected(['SPACE1', 'SPACE2']);
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.accept();

    expect(apiMock.startSelectedSpacesStream).toHaveBeenCalledWith(['SPACE1', 'SPACE2']);
    expect(pollingMock.start).toHaveBeenCalledWith(3000);
    expect(uiStateMock.selectedSpaces()).toEqual([]);
    expect(dataManagementMock.currentScanSpaceKeys()).toEqual(['SPACE1', 'SPACE2']);
    expect(dataManagementMock.queue()).toEqual(['SPACE1', 'SPACE2']);
    expect(apiMock.purgeAllScans).not.toHaveBeenCalled();
  });

  it('Should_ScanOnlyNewSelection_When_PreviousSelectedScanCompleted', () => {
    uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }]);
    service.startSelected(['SPACE1']);
    confirmMock.confirm.mock.calls[0][0].accept();
    pollingMock.actionPending.set(false);
    pollingMock.scanCompleted$.next();
    uiStateMock.selectedSpaces.update((spaces: { key: string }[]) => [...spaces, { key: 'SPACE2' }]);

    service.startSelected(uiStateMock.selectedSpaces().map((space: { key: string }) => space.key));
    confirmMock.confirm.mock.calls[1][0].accept();

    expect(apiMock.startSelectedSpacesStream).toHaveBeenLastCalledWith(['SPACE2']);
    expect(dataManagementMock.currentScanSpaceKeys()).toEqual(['SPACE2']);
  });

  it('Should_PreserveSelection_When_SelectedScanConfirmationRejected', () => {
    uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }]);

    service.startSelected(['SPACE1']);
    expect(uiStateMock.selectedSpaces()).toEqual([{ key: 'SPACE1' }]);
    confirmMock.confirm.mock.calls[0][0].reject();

    expect(uiStateMock.selectedSpaces()).toEqual([{ key: 'SPACE1' }]);
    expect(apiMock.startSelectedSpacesStream).not.toHaveBeenCalled();
  });

  it.each(['scanActive', 'actionPending'] as const)(
    'Should_PreserveSelection_When_SelectedScanBlockedBy_%s',
    (state) => {
      uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }]);
      pollingMock[state].set(true);

      service.startSelected(['SPACE1']);

      expect(uiStateMock.selectedSpaces()).toEqual([{ key: 'SPACE1' }]);
      expect(confirmMock.confirm).not.toHaveBeenCalled();
      expect(apiMock.startSelectedSpacesStream).not.toHaveBeenCalled();
    }
  );

  it('Should_PreserveNewSelection_When_RunningScanCompletes', () => {
    uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }]);
    service.startSelected(['SPACE1']);
    confirmMock.confirm.mock.calls[0][0].accept();
    uiStateMock.selectedSpaces.set([{ key: 'SPACE2' }]);

    pollingMock.scanCompleted$.next();

    expect(uiStateMock.selectedSpaces()).toEqual([{ key: 'SPACE2' }]);
  });

  it('Should_PreserveScopeAndNewSelection_When_SelectedScanResumes', () => {
    uiStateMock.selectedSpaces.set([{ key: 'SPACE1' }]);
    service.startSelected(['SPACE1']);
    confirmMock.confirm.mock.calls[0][0].accept();
    service.pauseScan();
    pollingMock.actionPending.set(false);
    uiStateMock.selectedSpaces.set([{ key: 'SPACE2' }]);

    service.resumeLastScan();

    expect(dataManagementMock.currentScanSpaceKeys()).toEqual(['SPACE1']);
    expect(uiStateMock.selectedSpaces()).toEqual([{ key: 'SPACE2' }]);
    expect(apiMock.resumeScan).toHaveBeenCalledWith('scan-1');
  });

  it('Should_ResetStalePendingBadges_When_RelaunchSelected', () => {
    utilsMock['allSpaces'] = vi.fn().mockReturnValue([
      { key: 'A', status: 'PENDING' },
      { key: 'B', status: 'PAUSED' },
      { key: 'C', status: 'NOT_STARTED' }
    ]);

    service.startSelected(['C']);
    confirmMock.confirm.mock.calls[0][0].accept();

    expect(utilsMock['updateSpace']).toHaveBeenCalledWith('A', { status: 'NOT_STARTED' });
    expect(utilsMock['updateSpace']).not.toHaveBeenCalledWith('B', { status: 'NOT_STARTED' });
  });

  // ========== pauseScan ==========

  it('Should_CallPauseApi_When_PauseScan', () => {
    service.pauseScan();

    expect(pollingMock.stop).toHaveBeenCalled();
    expect(apiMock.pauseScan).toHaveBeenCalledWith('scan-1');
  });

  it('Should_RefreshAfterPause_When_PauseSucceeds', () => {
    service.pauseScan();

    expect(pollingMock.forceRefresh).toHaveBeenCalled();
  });

  it('Should_RefreshAfterPause_When_PauseFails', () => {
    apiMock.pauseScan.mockReturnValue(throwError(() => new Error('fail')));
    service.pauseScan();

    expect(pollingMock.forceRefresh).toHaveBeenCalled();
  });

  it('Should_StillRefresh_When_NoScanId', () => {
    dataManagementMock.lastScanMeta.set(null);
    service.pauseScan();

    expect(apiMock.pauseScan).not.toHaveBeenCalled();
    expect(pollingMock.forceRefresh).toHaveBeenCalled();
  });

  // ========== resumeLastScan ==========

  it('Should_CallResumeApi_When_ResumeLastScan', () => {
    service.resumeLastScan();

    expect(apiMock.resumeScan).toHaveBeenCalledWith('scan-1');
  });

  it('Should_NotResume_When_NoMeta', () => {
    dataManagementMock.lastScanMeta.set(null);
    service.resumeLastScan();

    expect(apiMock.resumeScan).not.toHaveBeenCalled();
  });

  it('Should_NotResume_When_ActionPending', () => {
    pollingMock.actionPending.set(true);
    service.resumeLastScan();

    expect(apiMock.resumeScan).not.toHaveBeenCalled();
  });

  it('Should_StartPollingAndSse_When_ResumeSucceeds', () => {
    service.resumeLastScan();

    expect(pollingMock.start).toHaveBeenCalledWith(3000);
    expect(apiMock.startAllSpacesStream).toHaveBeenCalledWith('scan-1');
  });

  it('Should_ClearPending_When_ResumeFails', () => {
    apiMock.resumeScan.mockReturnValue(throwError(() => new Error('fail')));
    service.resumeLastScan();

    expect(pollingMock.actionPending()).toBe(false);
  });

  // ========== reconnectIfScanRunning ==========

  it('Should_SyncButtonPanel_When_Reconnect', () => {
    service.reconnectIfScanRunning();
    expect(pollingMock.forceRefresh).toHaveBeenCalled();
  });

  it('Should_ReconnectSse_When_ScanRunning', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([{ status: 'RUNNING' }]);
    service.reconnectIfScanRunning();

    expect(pollingMock.start).toHaveBeenCalled();
    expect(apiMock.startAllSpacesStream).toHaveBeenCalledWith('scan-1');
  });

  it('Should_NotReconnect_When_NoRunningSpaces', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([{ status: 'COMPLETED' }]);
    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  it('Should_RestoreScanScope_When_ReconnectWithMatchingScanId', () => {
    dataManagementMock.currentScanSpaceKeys.set(null);
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([
      { spaceKey: 'A', status: 'RUNNING', scanId: 'scan-1' },
      { spaceKey: 'B', status: 'NOT_STARTED', scanId: 'scan-1' },
      { spaceKey: 'C', status: 'OK', scanId: 'other-scan' }
    ]);

    service.reconnectIfScanRunning();

    expect(dataManagementMock.currentScanSpaceKeys()).toEqual(['A', 'B']);
  });

  it('Should_ReconnectSse_When_InterSpaceWindowNotStarted', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([
      { spaceKey: 'A', status: 'NOT_STARTED', scanId: 'scan-1' },
      { spaceKey: 'B', status: 'OK', scanId: 'scan-1' }
    ]);

    service.reconnectIfScanRunning();

    expect(pollingMock.start).toHaveBeenCalledWith(3000);
    expect(apiMock.startAllSpacesStream).toHaveBeenCalledWith('scan-1');
  });

  it('Should_NotReconnect_When_AllTerminalStatuses', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([
      { spaceKey: 'A', status: 'COMPLETED', scanId: 'scan-1' },
      { spaceKey: 'B', status: 'FAILED', scanId: 'scan-1' },
      { spaceKey: 'C', status: 'INTERRUPTED', scanId: 'scan-1' }
    ]);

    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  it('Should_NotReconnect_When_PausedInCurrentScan', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([
      { spaceKey: 'A', status: 'RUNNING', scanId: 'scan-1' },
      { spaceKey: 'B', status: 'PAUSED', scanId: 'scan-1' }
    ]);

    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  // ========== canPurgeData ==========

  it('Should_ComputeCanPurgeData_When_Idle', () => {
    expect(service.canPurgeData()).toBe(true);
  });

  it('Should_DisablePurge_When_ActionPending', () => {
    pollingMock.actionPending.set(true);
    expect(service.canPurgeData()).toBe(false);
  });

  it('Should_DisablePurge_When_ScanActive', () => {
    pollingMock.scanActive.set(true);
    expect(service.canPurgeData()).toBe(false);
  });

  // ========== purgeAllData ==========

  it('Should_ShowConfirmation_When_PurgeAllData', () => {
    service.purgeAllData();
    expect(confirmMock.confirm).toHaveBeenCalledTimes(1);
  });

  it('Should_NotPurge_When_ScanActive', () => {
    pollingMock.scanActive.set(true);
    service.purgeAllData();
    expect(confirmMock.confirm).not.toHaveBeenCalled();
  });

  it('Should_NotPurge_When_ActionPending', () => {
    pollingMock.actionPending.set(true);
    service.purgeAllData();
    expect(confirmMock.confirm).not.toHaveBeenCalled();
  });

  it('Should_CallPurgeApi_When_ConfirmAccepted', () => {
    service.purgeAllData();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.accept();

    expect(apiMock.purgeAllScans).toHaveBeenCalled();
    expect(storageMock.clearAllItems).toHaveBeenCalled();
    expect(pollingMock.actionPending()).toBe(false);
  });

  it('Should_LogCancellation_When_PurgeRejected', () => {
    service.purgeAllData();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.reject();

    expect(uiStateMock.append).toHaveBeenCalled();
    expect(apiMock.purgeAllScans).not.toHaveBeenCalled();
  });

  it('Should_HandleError_When_PurgeFails', () => {
    apiMock.purgeAllScans.mockReturnValue(throwError(() => new Error('purge failed')));
    service.purgeAllData();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];
    confirmCall.accept();

    expect(pollingMock.actionPending()).toBe(false);
    expect(uiStateMock.append).toHaveBeenCalled();
  });

  it('Should_UseDangerStyle_When_PurgeConfirmation', () => {
    service.purgeAllData();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];

    expect(confirmCall.acceptButtonStyleClass).toBe('p-button-danger');
    expect(confirmCall.icon).toBe('pi pi-trash');
  });

  // ========== executeStartAll error path ==========

  it('Should_ClearStreamingAndPending_When_PurgeFailsOnStartAll', () => {
    apiMock.purgeAllScans.mockReturnValue(throwError(() => new Error('purge boom')));
    service.startAll();
    const confirmCall = confirmMock.confirm.mock.calls[0][0];

    confirmCall.accept();

    expect(service.isStreaming()).toBe(false);
    expect(pollingMock.actionPending()).toBe(false);
    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  // ========== completion detection ==========

  it('Should_FinalizeScan_When_AllSpacesCompleted', () => {
    service.startAll();
    confirmMock.confirm.mock.calls[0][0].accept();

    pollingMock.scanCompleted$.next();

    expect(dataManagementMock.loadLastSpaceStatuses).toHaveBeenCalled();
    expect(dataManagementMock.loadLastScan).toHaveBeenCalled();
    expect(dataManagementMock.currentScanSpaceKeys()).toBeNull();
  });

  // ========== SSE error path ==========

  it('Should_StopStreaming_When_SseErrors', () => {
    const stream = new Subject<void>();
    apiMock.startAllSpacesStream.mockReturnValue(stream.asObservable());
    service.startAll();
    confirmMock.confirm.mock.calls[0][0].accept();
    expect(service.isStreaming()).toBe(true);

    stream.error(new Error('sse down'));

    expect(service.isStreaming()).toBe(false);
    expect(uiStateMock.append).toHaveBeenCalled();
  });

  // ========== SSE routing ==========

  it('Should_RouteEvent_When_SseEmitsItem', () => {
    const stream = new Subject<{ type: string; data: unknown }>();
    apiMock.startAllSpacesStream.mockReturnValue(stream.asObservable());
    service.startAll();
    confirmMock.confirm.mock.calls[0][0].accept();

    stream.next({ type: 'item', data: { pageId: 1 } });

    expect(sseMock.routeStreamEvent).toHaveBeenCalledWith('item', { pageId: 1 });
  });

  // ========== reconnectIfScanRunning extra branches ==========

  it('Should_NotReconnect_When_AlreadyStreaming', () => {
    service.startAll();
    confirmMock.confirm.mock.calls[0][0].accept();
    pollingMock.start.mockClear();

    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  it('Should_NotReconnect_When_PausedScan', () => {
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([{ status: 'RUNNING' }, { status: 'PAUSED' }]);
    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  it('Should_NotReconnect_When_NoScanMeta', () => {
    dataManagementMock.lastScanMeta.set(null);
    dataManagementMock.lastSpaceStatuses = vi.fn().mockReturnValue([{ status: 'RUNNING' }]);
    service.reconnectIfScanRunning();

    expect(pollingMock.start).not.toHaveBeenCalled();
  });

  // ========== reset ==========

  it('Should_StopEverything_When_Reset', () => {
    service.reset();

    expect(pollingMock.stop).toHaveBeenCalled();
    expect(pollingMock.actionPending()).toBe(false);
  });
});
