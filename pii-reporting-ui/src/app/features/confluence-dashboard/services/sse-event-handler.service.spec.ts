import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { vi, expect } from 'vitest';
import { SseEventHandlerService } from './sse-event-handler.service';
import { TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast.service';
import { PiiItemsStorageService } from './pii-items-storage.service';
import { DashboardUiStateService } from './dashboard-ui-state.service';
import { SpacesDashboardUtils } from '../spaces-dashboard.utils';

describe('SseEventHandlerService', () => {
  let service: SseEventHandlerService;
  let translocoMock: { translate: ReturnType<typeof vi.fn> };
  let toastMock: {
    showScanError: ReturnType<typeof vi.fn>;
    isScanPaused: ReturnType<typeof vi.fn>;
    isScanRefused: ReturnType<typeof vi.fn>;
  };
  let storageMock: { addPiiItemToSpace: ReturnType<typeof vi.fn> };
  let uiStateMock: { append: ReturnType<typeof vi.fn>; activeSpaceKey: ReturnType<typeof signal> };
  let utilsMock: { updateSpace: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    translocoMock = { translate: vi.fn((key: string) => key) };
    toastMock = {
      showScanError: vi.fn(),
      isScanPaused: vi.fn().mockReturnValue(false),
      isScanRefused: vi.fn().mockReturnValue(false)
    };
    storageMock = { addPiiItemToSpace: vi.fn().mockReturnValue(true) };
    uiStateMock = { append: vi.fn(), activeSpaceKey: signal('ACTIVE-SPACE') };
    utilsMock = { updateSpace: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        SseEventHandlerService,
        { provide: TranslocoService, useValue: translocoMock },
        { provide: ToastService, useValue: toastMock },
        { provide: PiiItemsStorageService, useValue: storageMock },
        { provide: DashboardUiStateService, useValue: uiStateMock },
        { provide: SpacesDashboardUtils, useValue: utilsMock }
      ]
    });
    service = TestBed.inject(SseEventHandlerService);
  });

  // ========== routeStreamEvent ==========

  it('Should_LogEvent_When_AnyEventReceived', () => {
    service.routeStreamEvent('keepalive', undefined);

    expect(uiStateMock.append).toHaveBeenCalled();
  });

  it('Should_DoNothing_When_PayloadIsUndefined', () => {
    service.routeStreamEvent('item', undefined);

    expect(storageMock.addPiiItemToSpace).not.toHaveBeenCalled();
  });

  // ========== item events ==========

  it('Should_AddItemToStorage_When_ItemEvent', () => {
    const payload = { spaceKey: 'SPACE1', pageId: 'page-1', detectedPIIs: [{ piiType: 'EMAIL' }] } as any;

    service.routeStreamEvent('item', payload);

    expect(storageMock.addPiiItemToSpace).toHaveBeenCalledWith('SPACE1', payload);
  });

  it('Should_AddItemToStorage_When_AttachmentItemEvent', () => {
    const payload = { spaceKey: 'SPACE1', attachmentName: 'file.pdf', detectedPIIs: [{ piiType: 'NAME' }] } as any;

    service.routeStreamEvent('attachmentItem', payload);

    expect(storageMock.addPiiItemToSpace).toHaveBeenCalledWith('SPACE1', payload);
  });

  it('Should_UseActiveSpaceKey_When_AttachmentMissingSpaceKey', () => {
    const payload = { attachmentName: 'file.pdf', attachmentUrl: 'url', detectedPIIs: [{ piiType: 'NAME' }] } as any;

    service.routeStreamEvent('attachmentItem', payload);

    expect(storageMock.addPiiItemToSpace).toHaveBeenCalledWith('ACTIVE-SPACE', payload);
    expect(uiStateMock.append).toHaveBeenCalledWith(expect.stringContaining('[DEBUG_LOG]'));
  });

  it('Should_SkipItem_When_NoSpaceKeyAndNotAttachment', () => {
    const payload = { pageId: 'page-1', detectedPIIs: [{ piiType: 'EMAIL' }] } as any;

    service.routeStreamEvent('item', payload);

    expect(storageMock.addPiiItemToSpace).not.toHaveBeenCalled();
  });

  // ========== scanError events ==========

  it('Should_ShowToast_When_ScanError', () => {
    const payload = {
      spaceKey: 'SPACE1',
      scanId: 'scan-1',
      errorKey: 'error.scan.page_timeout',
      errorParams: { page: 'Budget' }
    } as any;

    service.routeStreamEvent('scanError', payload);

    expect(toastMock.showScanError).toHaveBeenCalledWith(expect.objectContaining({
      spaceKey: 'SPACE1',
      scanId: 'scan-1',
      errorKey: 'error.scan.page_timeout',
      errorParams: { page: 'Budget' }
    }));
  });

  it('Should_UpdateTimestamp_When_ScanError', () => {
    const payload = { spaceKey: 'SPACE1', errorKey: 'error.scan.page_failed' } as any;

    service.routeStreamEvent('scanError', payload);

    expect(utilsMock.updateSpace).toHaveBeenCalledWith('SPACE1', expect.objectContaining({
      lastScanTs: expect.any(String)
    }));
  });

  it('Should_FallbackToActiveSpaceKey_When_ErrorMissingSpaceKey', () => {
    const payload = { errorKey: 'error.scan.page_failed' } as any;

    service.routeStreamEvent('scanError', payload);

    expect(toastMock.showScanError).toHaveBeenCalledWith(expect.objectContaining({
      spaceKey: 'ACTIVE-SPACE'
    }));
  });

  it('Should_SkipError_When_NoSpaceKeyAndNoActiveSpace', () => {
    uiStateMock.activeSpaceKey.set(null);
    const payload = { errorKey: 'error.scan.page_failed' } as any;

    service.routeStreamEvent('scanError', payload);

    expect(toastMock.showScanError).not.toHaveBeenCalled();
  });

  it('Should_StillNotify_When_DetectorUnreachableAndNoSpaceKey', () => {
    // A multi-space scan refusal carries no space key; dropping it would put the
    // operator back in front of an unexplained empty scan.
    uiStateMock.activeSpaceKey.set(null);
    toastMock.isScanRefused.mockReturnValue(true);
    const payload = {
      scanId: 'scan-9',
      errorKey: 'error.scan.detector_endpoint_unreachable',
      errorParams: { detector: 'MINISTRAL' }
    } as any;

    service.routeStreamEvent('scanError', payload);

    expect(toastMock.showScanError).toHaveBeenCalledWith(expect.objectContaining({
      scanId: 'scan-9',
      errorKey: 'error.scan.detector_endpoint_unreachable'
    }));
  });

  it('Should_LogTheBackendWording_When_ErrorCarriesOne', () => {
    // The event log is a technical trail, so it keeps the backend's own diagnostic line.
    const payload = { spaceKey: 'SPACE1', message: 'error.scan.detection_failed {status=UNAVAILABLE}' } as any;

    service.routeStreamEvent('scanError', payload);

    expect(uiStateMock.append).toHaveBeenCalledWith(
      expect.stringContaining('error.scan.detection_failed'));
  });

  it('Should_NotifyOnceWithoutPageDetails_When_ScanWasPausedByOutage', () => {
    toastMock.isScanPaused.mockReturnValue(true);
    const payload = {
      spaceKey: 'SPACE1',
      scanId: 'scan-1',
      pageId: 'page-7',
      pageTitle: 'Page 7',
      errorKey: 'error.scan.paused_detector',
      errorParams: { cause: 'Detector MINISTRAL could not run' }
    } as any;

    service.routeStreamEvent('scanError', payload);

    // Reported as one scan-wide outage: attributing it to the page that happened to hit
    // it first would read as an isolated item failure.
    expect(toastMock.showScanError).toHaveBeenCalledTimes(1);
    const notified = toastMock.showScanError.mock.calls[0][0];
    expect(notified.errorKey).toBe('error.scan.paused_detector');
    expect(notified.pageId).toBeUndefined();
    expect(notified.pageTitle).toBeUndefined();
  });

  it('Should_NotMarkSpaceActivity_When_ScanWasPausedByOutage', () => {
    toastMock.isScanPaused.mockReturnValue(true);
    const payload = { spaceKey: 'SPACE1', errorKey: 'error.scan.paused_network' } as any;

    service.routeStreamEvent('scanError', payload);

    // Nothing was scanned: refreshing the space activity timestamp would suggest otherwise.
    expect(utilsMock.updateSpace).not.toHaveBeenCalled();
  });

  it('Should_NotifyPausedScan_When_NoSpaceKeyIsKnown', () => {
    uiStateMock.activeSpaceKey.set(null);
    toastMock.isScanPaused.mockReturnValue(true);
    const payload = { errorKey: 'error.scan.paused_network' } as any;

    service.routeStreamEvent('scanError', payload);

    // The cause is the network, not a space: dropping it for lack of a space key would
    // leave the operator with a stopped scan and no explanation.
    expect(toastMock.showScanError).toHaveBeenCalledTimes(1);
  });

  // ========== Status events (ignored by SSE handler) ==========

  it('Should_OnlyLog_When_StatusEvent', () => {
    service.routeStreamEvent('start', { spaceKey: 'SPACE1' } as any);
    service.routeStreamEvent('complete', { spaceKey: 'SPACE1' } as any);
    service.routeStreamEvent('multiStart', {} as any);
    service.routeStreamEvent('multiComplete', {} as any);
    service.routeStreamEvent('pageStart', { spaceKey: 'SPACE1' } as any);

    expect(storageMock.addPiiItemToSpace).not.toHaveBeenCalled();
    expect(toastMock.showScanError).not.toHaveBeenCalled();
    // Only logging should have happened
    expect(uiStateMock.append).toHaveBeenCalledTimes(5);
  });
});
