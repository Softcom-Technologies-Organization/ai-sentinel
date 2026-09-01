import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfluenceDashboardComponent } from './confluence-dashboard.component';
import { ConfluenceConnectionConfigService } from '../../core/services/confluence-connection-config.service';
import { ConfluenceConnectionConfig } from '../../core/models/confluence-connection-config.model';
import { SpaceDataManagementService } from './services/space-data-management.service';
import { SpaceFilteringService } from './services/space-filtering.service';
import { DashboardUiStateService } from './services/dashboard-ui-state.service';
import { PiiItemsStorageService } from './services/pii-items-storage.service';
import { ScanControlService } from './services/scan-control.service';
import { FilterUrlStateService } from './services/filter-url-state.service';
import { SpacesDashboardUtils } from './spaces-dashboard.utils';

function config(configured: boolean): ConfluenceConnectionConfig {
  return {
    baseUrl: 'https://example.atlassian.net',
    username: 'user@example.com',
    connectTimeout: 5000,
    readTimeout: 30000,
    maxRetries: 3,
    pagesLimit: 25,
    maxPages: 100,
    deploymentType: 'CLOUD',
    configured
  };
}

/** The browser reports a refused connection as status 0 with no body. */
const CONNECTION_REFUSED = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });

describe('Confluence dashboard configuration check', () => {
  let getConfig: ReturnType<typeof vi.fn>;
  let dataManagement: {
    isSpacesLoading: ReturnType<typeof signal<boolean>>;
    fetchSpaces: ReturnType<typeof vi.fn>;
    loadLastScan: ReturnType<typeof vi.fn>;
    loadSpacesUpdateInfo: ReturnType<typeof vi.fn>;
    loadLastSpaceStatuses: ReturnType<typeof vi.fn>;
    stopBackgroundPolling: ReturnType<typeof vi.fn>;
  };

  function createComponent(): ConfluenceDashboardComponent {
    return TestBed.runInInjectionContext(() => new ConfluenceDashboardComponent());
  }

  let filtering: { reload: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    getConfig = vi.fn();
    filtering = { reload: vi.fn() };
    dataManagement = {
      isSpacesLoading: signal(true),
      fetchSpaces: vi.fn().mockReturnValue(of(undefined)),
      loadLastScan: vi.fn().mockReturnValue(of(undefined)),
      loadSpacesUpdateInfo: vi.fn().mockReturnValue(of(undefined)),
      loadLastSpaceStatuses: vi.fn().mockReturnValue(of(undefined)),
      stopBackgroundPolling: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: ConfluenceConnectionConfigService, useValue: { getConfig } },
        { provide: SpaceDataManagementService, useValue: dataManagement },
        { provide: SpaceFilteringService, useValue: filtering },
        { provide: DashboardUiStateService, useValue: {} },
        { provide: PiiItemsStorageService, useValue: {} },
        { provide: ScanControlService, useValue: { reconnectIfScanRunning: vi.fn() } },
        { provide: FilterUrlStateService, useValue: {} },
        { provide: SpacesDashboardUtils, useValue: {} }
      ]
    });
  });

  it('Should_ReportBackendUnreachable_When_ConfigCallFails', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));

    const component = createComponent();
    component.ngOnInit();

    expect(component.backendUnreachable()).toBe(true);
    expect(component.confluenceConfigMissing()).toBe(false);
    expect(dataManagement.isSpacesLoading()).toBe(false);
    expect(dataManagement.fetchSpaces).not.toHaveBeenCalled();
  });

  it('Should_ReportMissingCredentials_When_BackendAnswersNotConfigured', () => {
    getConfig.mockReturnValue(of(config(false)));

    const component = createComponent();
    component.ngOnInit();

    expect(component.confluenceConfigMissing()).toBe(true);
    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).not.toHaveBeenCalled();
  });

  it('Should_LoadData_When_BackendAnswersConfigured', () => {
    getConfig.mockReturnValue(of(config(true)));

    const component = createComponent();
    component.ngOnInit();

    expect(component.confluenceConfigMissing()).toBe(false);
    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).toHaveBeenCalled();
  });

  it('Should_ClearBackendUnreachable_When_RetrySucceeds', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));
    const component = createComponent();
    component.ngOnInit();

    getConfig.mockReturnValue(of(config(true)));
    component.retryBackendConnection();

    expect(component.backendUnreachable()).toBe(false);
    expect(dataManagement.fetchSpaces).toHaveBeenCalled();
  });

  it('Should_RefetchServerOrdering_When_RetrySucceeds', () => {
    getConfig.mockReturnValue(throwError(() => CONNECTION_REFUSED));
    const component = createComponent();
    component.ngOnInit();

    getConfig.mockReturnValue(of(config(true)));
    component.retryBackendConnection();

    // Restocking the space store is not enough: the table renders rows in the server-decided
    // order, so the ordering query has to run again or the table stays empty.
    expect(filtering.reload).toHaveBeenCalledTimes(1);
  });
});
