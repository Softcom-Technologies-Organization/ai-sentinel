import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SentinelleApiService } from './sentinelle-api.service';

describe('SentinelleApiService', () => {
  let service: SentinelleApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SentinelleApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SentinelleApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadRevealConfig', () => {
    it('should update revealAllowed signal on success', () => {
      service.loadRevealConfig().subscribe((result) => {
        expect(result).toBe(true);
      });

      const req = httpMock.expectOne('/api/v1/pii/config/reveal-allowed');
      expect(req.request.method).toBe('GET');
      req.flush(true);

      expect(service.revealAllowed()).toBe(true);
    });

    it('should set revealAllowed to false on error', () => {
      service.loadRevealConfig().subscribe((result) => {
        expect(result).toBe(false);
      });

      const req = httpMock.expectOne('/api/v1/pii/config/reveal-allowed');
      req.flush('error', { status: 500, statusText: 'Server Error' });

      expect(service.revealAllowed()).toBe(false);
    });
  });

  describe('getSpaces', () => {
    it('should map and filter spaces correctly', () => {
      const apiResponse = [
        { key: 'DEV', name: 'Development', url: 'https://wiki/dev' },
        { key: 'HR', name: null, url: null },
        { key: '', name: 'Empty Key', url: null },
      ];

      service.getSpaces().subscribe((spaces) => {
        expect(spaces).toHaveLength(2);
        expect(spaces[0]).toEqual({ key: 'DEV', name: 'Development', url: 'https://wiki/dev' });
        expect(spaces[1]).toEqual({ key: 'HR', name: '', url: undefined });
      });

      const req = httpMock.expectOne('/api/v1/confluence/spaces');
      expect(req.request.method).toBe('GET');
      req.flush(apiResponse);
    });
  });

  describe('getLastScanMeta', () => {
    it('should return scan metadata on success', () => {
      const meta = { scanId: 'scan-1', lastUpdated: '2026-01-01', spacesCount: 3 };

      service.getLastScanMeta().subscribe((result) => {
        expect(result).toEqual(meta);
      });

      const req = httpMock.expectOne('/api/v1/scans/last');
      req.flush(meta);
    });

    it('should return null on error', () => {
      service.getLastScanMeta().subscribe((result) => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne('/api/v1/scans/last');
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('getLastScanSpaceStatuses', () => {
    it('should return space statuses array', () => {
      const statuses = [{ spaceKey: 'DEV', status: 'COMPLETED', pagesDone: 10, attachmentsDone: 2, lastEventTs: '' }];

      service.getLastScanSpaceStatuses().subscribe((result) => {
        expect(result).toEqual(statuses);
      });

      const req = httpMock.expectOne('/api/v1/scans/last/spaces');
      req.flush(statuses);
    });

    it('should return empty array on error', () => {
      service.getLastScanSpaceStatuses().subscribe((result) => {
        expect(result).toEqual([]);
      });

      const req = httpMock.expectOne('/api/v1/scans/last/spaces');
      req.flush('error', { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getDashboardSpacesSummary', () => {
    it('should return summary on success', () => {
      const summary = { scanId: 'scan-1', lastUpdated: '2026-01-01', spacesCount: 1, spaces: [] };

      service.getDashboardSpacesSummary().subscribe((result) => {
        expect(result).toEqual(summary);
      });

      const req = httpMock.expectOne('/api/v1/scans/dashboard/spaces-summary');
      req.flush(summary);
    });

    it('should return null on error', () => {
      service.getDashboardSpacesSummary().subscribe((result) => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne('/api/v1/scans/dashboard/spaces-summary');
      req.flush('error', { status: 500, statusText: 'Server Error' });
    });
  });

  describe('resumeScan', () => {
    it('should POST to resume endpoint with encoded scanId', () => {
      service.resumeScan('scan/123').subscribe();

      const req = httpMock.expectOne('/api/v1/stream/scan%2F123/resume');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(null);
    });
  });

  describe('pauseScan', () => {
    it('should POST to pause endpoint with encoded scanId', () => {
      service.pauseScan('scan-456').subscribe();

      const req = httpMock.expectOne('/api/v1/stream/scan-456/pause');
      expect(req.request.method).toBe('POST');
      req.flush(null);
    });
  });

  describe('purgeAllScans', () => {
    it('should POST to purge endpoint', () => {
      service.purgeAllScans().subscribe();

      const req = httpMock.expectOne('/api/v1/scans/purge');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(null);
    });
  });

  describe('revealPageSecrets', () => {
    it('should POST scanId and pageId and return response', () => {
      const response = { scanId: 's1', pageId: 'p1', pageTitle: 'Test', secrets: [] };

      service.revealPageSecrets('s1', 'p1').subscribe((result) => {
        expect(result).toEqual(response);
      });

      const req = httpMock.expectOne('/api/v1/pii/reveal-page');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ scanId: 's1', pageId: 'p1' });
      req.flush(response);
    });
  });
});
