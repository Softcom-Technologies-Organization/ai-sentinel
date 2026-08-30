import { TestBed } from '@angular/core/testing';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';
import { ToastService } from './toast.service';
import { MessageService } from 'primeng/api';
import fr from '../../../assets/i18n/fr.json';
import en from '../../../assets/i18n/en.json';

describe('ToastService', () => {
  let service: ToastService;
  let msgMock: { add: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    msgMock = { add: vi.fn(), clear: vi.fn() };

    // The real translation files: what the operator actually reads is the thing under test,
    // so a copy inlined here would pass while the shipped dashboard shows a raw key.
    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr', 'en'] },
          preloadLangs: true,
          langs: { fr, en }
        })
      ],
      providers: [
        ToastService,
        { provide: MessageService, useValue: msgMock }
      ]
    });
    service = TestBed.inject(ToastService);
  });

  const lastToast = () => msgMock.add.mock.calls[0][0];

  it('Should_AddStickyError_When_ShowScanError', () => {
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      pageTitle: 'Test Page',
      errorKey: 'error.scan.page_timeout',
      errorParams: { page: 'Test Page' }
    });

    expect(msgMock.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      sticky: true,
      key: 'scan-errors'
    }));
  });

  it('Should_ClearScanErrors_When_ClearScanErrors', () => {
    service.clearScanErrors();
    expect(msgMock.clear).toHaveBeenCalledWith('scan-errors');
  });

  it('Should_ShowFrenchSentence_When_DetectorModelIsNotLoaded', () => {
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: '',
      errorKey: 'error.scan.detector_model_not_loaded',
      errorParams: {
        detector: 'MINISTRAL',
        endpoint: 'http://localhost:1234/v1',
        model: 'ministral-3b-pii-preview@q8_0',
        state: 'not-loaded'
      }
    });

    const toast = lastToast();
    expect(toast.summary).toBe('Modèle du détecteur non chargé — scan non démarré');
    expect(toast.detail).toContain('ministral-3b-pii-preview@q8_0');
    expect(toast.detail).toContain('http://localhost:1234/v1');
    expect(toast.detail).toContain('not-loaded');
    // No English sentence built by the backend ever reaches the operator.
    expect(toast.detail).not.toContain('is present but not loaded');
  });

  it('Should_ShowEnglishSentence_When_LanguageIsEnglish', () => {
    TestBed.inject(TranslocoService).setActiveLang('en');

    service.showScanError({
      scanId: 'scan-1',
      spaceKey: '',
      errorKey: 'error.scan.detector_model_not_loaded',
      errorParams: {
        detector: 'MINISTRAL',
        endpoint: 'http://localhost:1234/v1',
        model: 'ministral-3b-pii-preview@q8_0',
        state: 'not-loaded'
      }
    });

    const toast = lastToast();
    expect(toast.summary).toBe('Detector model not loaded — scan not started');
    expect(toast.detail).toContain('ministral-3b-pii-preview@q8_0');
  });

  it('Should_FallBackToGenericWording_When_KeyIsUnknownToThisDashboard', () => {
    // A backend newer than this dashboard must still produce a readable notification
    // rather than showing the operator a raw translation key.
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      errorKey: 'error.scan.some_future_failure',
      errorParams: { cause: 'quota exhausted' }
    });

    const toast = lastToast();
    expect(toast.summary).toBe('Erreur de scan');
    expect(toast.detail).toContain('quota exhausted');
    expect(toast.detail).not.toContain('error.scan');
  });

  it('Should_FallBackToGenericWording_When_NoKeyWasSent', () => {
    service.showScanError({ scanId: 'scan-1', spaceKey: 'SPACE1' });

    expect(lastToast().summary).toBe('Erreur de scan');
  });

  it('Should_TellOperatorToResume_When_ScanWasPaused', () => {
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      errorKey: 'error.scan.paused_network',
      errorParams: { cause: 'Unable to connect to the data source.' }
    });

    const toast = lastToast();
    expect(toast.summary).toBe('Scan mis en pause — source de données injoignable');
    expect(toast.detail).toContain('Unable to connect to the data source.');
    // The actionable part: progress is kept, so Resume picks the scan back up.
    expect(toast.detail).toContain('Reprendre');
  });

  it('Should_NotNamePageOrSpace_When_FailureIsScanWide', () => {
    // A refusal caused by a detector belongs to no page: naming one would send the
    // operator looking at content that has nothing to do with it.
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      pageTitle: 'Budget 2026',
      errorKey: 'error.scan.detector_endpoint_unreachable',
      errorParams: { detector: 'MINISTRAL', endpoint: 'http://lmstudio:1234/v1', cause: 'connection refused' }
    });

    const toast = lastToast();
    expect(toast.detail).not.toContain('Budget 2026');
    expect(toast.detail).not.toContain('SPACE1');
  });

  it('Should_NameTheItem_When_FailureIsBoundToOneItem', () => {
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      attachmentName: 'report.pdf',
      errorKey: 'error.scan.attachment_timeout',
      errorParams: { attachment: 'report.pdf' }
    });

    const toast = lastToast();
    expect(toast.detail).toContain('report.pdf');
    expect(toast.detail).toContain('SPACE1');
  });

  it('Should_ShowPageId_When_NoPageTitle', () => {
    service.showScanError({
      scanId: 'scan-1',
      spaceKey: 'SPACE1',
      pageId: '12345',
      errorKey: 'error.scan.page_failed',
      errorParams: { page: '', cause: 'boom' }
    });

    expect(lastToast().detail).toContain('12345');
  });

  it('Should_ReportPausedScan_When_OutageStoppedIt', () => {
    expect(service.isScanPaused('error.scan.paused_detector')).toBe(true);
    expect(service.isScanPaused('error.scan.paused_network')).toBe(true);
  });

  it('Should_NotReportPausedScan_When_OnlyOneItemFailed', () => {
    // An expected item failure must never be presented as a stopped scan.
    expect(service.isScanPaused('error.scan.detection_timeout')).toBe(false);
    expect(service.isScanPaused('error.scan.page_failed')).toBe(false);
    expect(service.isScanPaused(undefined)).toBe(false);
  });

  it('Should_ReportRefusedScan_When_AnEnabledDetectorCannotRun', () => {
    expect(service.isScanRefused('error.scan.detector_model_not_loaded')).toBe(true);
    expect(service.isScanRefused('error.scan.detector_endpoint_unreachable')).toBe(true);
    // A detector that went down MID-scan is a pause, not a refusal: there is something to resume.
    expect(service.isScanRefused('error.scan.paused_detector')).toBe(false);
    expect(service.isScanRefused(undefined)).toBe(false);
  });
});
