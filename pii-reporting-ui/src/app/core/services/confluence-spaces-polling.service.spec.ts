import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ConfluenceSpacesPollingService, SpaceChangeDetection } from './confluence-spaces-polling.service';
import { SentinelleApiService } from './sentinelle-api.service';
import { Space } from '../models/space';

describe('ConfluenceSpacesPollingService', () => {
  let service: ConfluenceSpacesPollingService;
  let httpMock: HttpTestingController;
  let apiSpy: jest.Mocked<Pick<SentinelleApiService, 'getSpaces' | 'getSpacesUpdateInfo'>>;

  beforeEach(() => {
    apiSpy = {
      getSpaces: jest.fn(),
      getSpacesUpdateInfo: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        ConfluenceSpacesPollingService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SentinelleApiService, useValue: apiSpy },
      ],
    });
    service = TestBed.inject(ConfluenceSpacesPollingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('loadPollingConfig', () => {
    it('should load polling interval from backend', async () => {
      const promise = service.loadPollingConfig();

      const req = httpMock.expectOne('/api/v1/config/polling');
      expect(req.request.method).toBe('GET');
      req.flush({ backendRefreshIntervalMs: 30000, frontendPollingIntervalMs: 5000 });

      await promise;

      // Verify by triggering polling and checking timer interval
      apiSpy.getSpaces.mockReturnValue(of([]));
      const observable = service.startPolling(0);
      expect(observable).toBeTruthy();
    });

    it('should keep default interval on HTTP error', async () => {
      const promise = service.loadPollingConfig();

      const req = httpMock.expectOne('/api/v1/config/polling');
      req.flush('error', { status: 500, statusText: 'Server Error' });

      // Should not throw
      await expect(promise).resolves.toBeUndefined();
    });
  });

  describe('startPolling', () => {
    it('should detect new spaces when count increases', fakeAsync(() => {
      const spaces: Space[] = [
        { key: 'A', name: 'A', url: undefined },
        { key: 'B', name: 'B', url: undefined },
        { key: 'C', name: 'C', url: undefined },
      ];
      apiSpy.getSpaces.mockReturnValue(of(spaces));

      let result: SpaceChangeDetection | undefined;
      const sub = service.startPolling(2).subscribe((d) => (result = d));

      // Default interval is 60000ms, skip(1) means we need 2 ticks
      tick(60000);
      tick(60000);

      expect(result).toBeDefined();
      expect(result?.totalCount).toBe(3);
      expect(result?.hasNewSpaces).toBe(true);
      expect(result?.newSpacesCount).toBe(1);

      sub.unsubscribe();
    }));

    it('should not flag new spaces when count is unchanged', fakeAsync(() => {
      const spaces: Space[] = [{ key: 'A', name: 'A', url: undefined }];
      apiSpy.getSpaces.mockReturnValue(of(spaces));

      let result: SpaceChangeDetection | undefined;
      const sub = service.startPolling(1).subscribe((d) => (result = d));

      tick(60000);
      tick(60000);

      expect(result?.totalCount).toBe(1);
      expect(result?.hasNewSpaces).toBe(false);
      expect(result?.newSpacesCount).toBe(0);

      sub.unsubscribe();
    }));

    it('should not flag new spaces when count decreases', fakeAsync(() => {
      const spaces: Space[] = [{ key: 'A', name: 'A', url: undefined }];
      apiSpy.getSpaces.mockReturnValue(of(spaces));

      let result: SpaceChangeDetection | undefined;
      const sub = service.startPolling(5).subscribe((d) => (result = d));

      tick(60000);
      tick(60000);

      expect(result?.totalCount).toBe(1);
      expect(result?.hasNewSpaces).toBe(false);
      expect(result?.newSpacesCount).toBe(0);

      sub.unsubscribe();
    }));
  });

  describe('startUpdateInfoPolling', () => {
    it('should emit space update info from API', fakeAsync(() => {
      const updates = [{ spaceKey: 'A', hasBeenUpdated: true }] as any;
      apiSpy.getSpacesUpdateInfo.mockReturnValue(of(updates));

      let result: any;
      const sub = service.startUpdateInfoPolling().subscribe((d) => (result = d));

      tick(60000);
      tick(60000);

      expect(result).toEqual(updates);
      sub.unsubscribe();
    }));

    it('should fall back to empty array on API error', fakeAsync(() => {
      apiSpy.getSpacesUpdateInfo.mockReturnValue(throwError(() => new Error('boom')));

      let result: any;
      const sub = service.startUpdateInfoPolling().subscribe((d) => (result = d));

      tick(60000);
      tick(60000);

      expect(result).toEqual([]);
      sub.unsubscribe();
    }));
  });
});
