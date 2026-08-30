import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SpaceScanStatsPopoverComponent } from './space-scan-stats-popover.component';
import { SentinelleApiService, SpaceScanStatsDto } from '../../../../core/services/sentinelle-api.service';

const FR_TRANSLATIONS = {
  dashboard: {
    scanStats: {
      buttonAriaLabel: 'Voir les statistiques du dernier scan',
      title: 'Statistiques du dernier scan',
      loading: 'Chargement des statistiques...',
      notAvailable: 'Statistiques non disponibles pour ce scan',
      error: 'Erreur lors du chargement des statistiques',
      duration: 'Durée du scan',
      inProgress: 'Scan en cours',
      scanned: 'Analysés',
      failed: 'En échec',
      chars: 'Caractères',
      pages: 'Pages',
      attachments: 'Pièces jointes',
      failedItems: 'Items en échec',
      detectors: 'Détecteurs',
      detector: 'Détecteur',
      detections: 'Détections',
      discarded: 'Écartées',
      rate: 'Débit moyen',
      rateUnit: 'car/s',
      busy: 'Temps cumulé',
      prefilterLabel: 'Pré-filtre',
      detectorFailed: '{{count}} échec(s) — analyse incomplète',
      busyNote: 'Temps de calcul cumulé.'
    }
  }
};

const COMPLETED_STATS: SpaceScanStatsDto = {
  scanId: 'scan-1',
  spaceKey: 'KEY',
  startedAt: '2026-01-01T00:00:00Z',
  finishedAt: '2026-01-01T00:12:34Z',
  durationMs: 754000,
  pagesScanned: 42,
  pagesFailed: 1,
  pageChars: 1200000,
  attachmentsScanned: 7,
  attachmentsFailed: 2,
  attachmentChars: 530000,
  failedItems: [{ itemType: 'PAGE', title: 'Ma page' }],
  detectorStats: [
    { detector: 'PRESIDIO', detections: 12, charsProcessed: 1730000, busyMs: 520000, charsPerSecond: 3326.9, discarded: 0, failedRequests: 0, lastError: null },
    { detector: 'PREFILTER', detections: 50, charsProcessed: 0, busyMs: 12, charsPerSecond: null, discarded: 5, failedRequests: 0, lastError: null }
  ]
};

describe('SpaceScanStatsPopoverComponent', () => {
  let fixture: ComponentFixture<SpaceScanStatsPopoverComponent>;
  let apiMock: { getSpaceScanStats: ReturnType<typeof vi.fn> };

  function createComponent(): void {
    fixture = TestBed.createComponent(SpaceScanStatsPopoverComponent);
    fixture.componentRef.setInput('spaceKey', 'KEY');
    fixture.detectChanges();
  }

  beforeEach(async () => {
    apiMock = { getSpaceScanStats: vi.fn().mockReturnValue(of(COMPLETED_STATS)) };

    await TestBed.configureTestingModule({
      imports: [
        SpaceScanStatsPopoverComponent,
        TranslocoTestingModule.forRoot({
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          preloadLangs: true,
          langs: { fr: FR_TRANSLATIONS }
        })
      ],
      providers: [{ provide: SentinelleApiService, useValue: apiMock }]
    }).compileComponents();
  });

  it('Should_StayIdle_When_NotOpened', () => {
    createComponent();
    expect(fixture.componentInstance.state()).toBe('idle');
    expect(apiMock.getSpaceScanStats).not.toHaveBeenCalled();
  });

  it('Should_LoadStats_When_PopoverShown', () => {
    createComponent();

    fixture.componentInstance.onShow();

    expect(apiMock.getSpaceScanStats).toHaveBeenCalledWith('KEY');
    expect(fixture.componentInstance.state()).toBe('loaded');
    expect(fixture.componentInstance.stats()).toEqual(COMPLETED_STATS);
  });

  it('Should_SetNotFound_When_StatsMissing', () => {
    apiMock.getSpaceScanStats.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 }))
    );
    createComponent();

    fixture.componentInstance.onShow();

    expect(fixture.componentInstance.state()).toBe('notFound');
    expect(fixture.componentInstance.stats()).toBeNull();
  });

  it('Should_SetError_When_NetworkFails', () => {
    apiMock.getSpaceScanStats.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    createComponent();

    fixture.componentInstance.onShow();

    expect(fixture.componentInstance.state()).toBe('error');
  });

  it('Should_FlagInProgress_When_DurationNull', () => {
    apiMock.getSpaceScanStats.mockReturnValue(
      of({ ...COMPLETED_STATS, durationMs: null, finishedAt: null })
    );
    createComponent();

    fixture.componentInstance.onShow();

    expect(fixture.componentInstance.isScanInProgress()).toBe(true);
  });

  it('Should_FormatDurationAsHms_When_OverOneMinute', () => {
    createComponent();
    expect(fixture.componentInstance.formatDuration(754000)).toBe('00:12:34');
  });

  it('Should_FormatDurationAsSeconds_When_UnderOneMinute', () => {
    createComponent();
    expect(fixture.componentInstance.formatDuration(45000)).toBe('45s');
  });

  it('Should_FormatCharsCompactly_When_LargeValue', () => {
    createComponent();
    expect(fixture.componentInstance.formatChars(1200000)).toBe('1,2 M');
    expect(fixture.componentInstance.formatChars(5300)).toBe('5,3 k');
    expect(fixture.componentInstance.formatChars(530)).toBe('530');
  });

  it('Should_RenderDash_When_RateNull', () => {
    createComponent();
    expect(fixture.componentInstance.formatRate(null)).toBe('—');
  });

  it('Should_IdentifyPrefilter_When_DetectorIsPrefilter', () => {
    createComponent();
    const c = fixture.componentInstance;
    expect(c.isPrefilter('PREFILTER')).toBe(true);
  });

  it('Should_ExposeDiscardedCounts_When_StatsLoaded', () => {
    createComponent();
    fixture.componentInstance.onShow();
    const loaded = fixture.componentInstance.stats();
    const prefilter = loaded?.detectorStats.find((d) => d.detector === 'PREFILTER');
    expect(prefilter?.discarded).toBe(5);
  });

  it('Should_ExposeFailureCountAndReason_When_DetectorCouldNotRun', () => {
    apiMock.getSpaceScanStats.mockReturnValue(of({
      ...COMPLETED_STATS,
      detectorStats: [{
        detector: 'MINISTRAL', detections: 0, charsProcessed: 12000, busyMs: 90000,
        charsPerSecond: 133.3, discarded: 0, failedRequests: 7,
        lastError: 'ConnectError: connection refused'
      }]
    }));
    createComponent();

    fixture.componentInstance.onShow();

    // Zero detections alone would read as a clean space; the template drives its
    // warning off these two fields, which the popover must carry through.
    const ministral = fixture.componentInstance.stats()?.detectorStats[0];
    expect(ministral?.detections).toBe(0);
    expect(ministral?.failedRequests).toBe(7);
    expect(ministral?.lastError).toBe('ConnectError: connection refused');
  });

  it('Should_ReportNoFailure_When_DetectorsRanNormally', () => {
    createComponent();

    fixture.componentInstance.onShow();

    const loaded = fixture.componentInstance.stats();
    expect(loaded?.detectorStats.every((d) => d.failedRequests === 0)).toBe(true);
  });
});
