import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SentinelleApiService, SpaceScanStatsDto } from './sentinelle-api.service';

describe('SentinelleApiService.getSpaceScanStats', () => {
  let service: SentinelleApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SentinelleApiService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(SentinelleApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('Should_RequestStatsEndpoint_When_SpaceKeyProvided', () => {
    let received: SpaceScanStatsDto | undefined;
    service.getSpaceScanStats('KEY').subscribe((stats) => (received = stats));

    const request = httpTesting.expectOne('/api/v1/scans/dashboard/spaces/KEY/stats');
    expect(request.request.method).toBe('GET');

    const payload: SpaceScanStatsDto = {
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
      detectorStats: [{ detector: 'PRESIDIO', detections: 12, charsProcessed: 1730000, busyMs: 520000, charsPerSecond: 3326.9, discarded: 0, failedRequests: 0, lastError: null }]
    };
    request.flush(payload);

    expect(received).toEqual(payload);
  });

  it('Should_EncodeSpaceKey_When_KeyContainsSpecialChars', () => {
    service.getSpaceScanStats('A B/C').subscribe();

    const request = httpTesting.expectOne('/api/v1/scans/dashboard/spaces/A%20B%2FC/stats');
    expect(request.request.url).toBe('/api/v1/scans/dashboard/spaces/A%20B%2FC/stats');
    request.flush({} as SpaceScanStatsDto);
  });

  it('Should_PropagateError_When_NotFound', () => {
    let error: HttpErrorResponse | undefined;
    service.getSpaceScanStats('KEY').subscribe({ error: (err) => (error = err) });

    httpTesting
      .expectOne('/api/v1/scans/dashboard/spaces/KEY/stats')
      .flush('Not found', { status: 404, statusText: 'Not Found' });

    expect(error?.status).toBe(404);
  });
});
